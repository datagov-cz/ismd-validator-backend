package com.dia.transformer;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three ciselniky-test fixtures (Excel, Archi, EA) describe the same codelists, including
 * invalid cases. Each format must produce the same codelists in JSON-LD and in Turtle.
 */
@SpringBootTest
@ActiveProfiles("test")
class CodelistConversionTest {

    private static final String FIXTURES = "com/dia/canonical/complete/";

    private static final String SLOVNIKY = "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/";
    private static final String L111 = "https://slovník.gov.cz/legislativní/sbírka/111/2009/pojem/";

    private record Codelist(String iri, String nkodDataset) {
    }

    /** Concept name → expected codelist. Concepts not listed must have no codelist. */
    private static final Map<String, Codelist> EXPECTED = Map.of(
            "Řidič", new Codelist(
                    "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/číselník/právní-formy",
                    "https://data.gov.cz/zdroj/datové-sady/17651921/c0fd2b9b10627a75e932e92c3441db84"),
            "Řidičský průkaz", new Codelist(
                    "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/číselník/typy-datových-schránek",
                    "https://data.gov.cz/zdroj/datové-sady/17651921/075f0ac2a20db0a1a89638c7ed88d974"),
            "Vozidlo", new Codelist(
                    "https://data.mvcr.gov.cz/zdroj/číselníky/typy-turistických-cílů",
                    "https://data.gov.cz/zdroj/datové-sady/17651921/ff931872553062c9890157ce8615af03"),
            // codelist without an NKOD dataset
            "Řidič evidovaný v registru řidičů", new Codelist(
                    "https://data.dia.gov.cz/zdroj/číselníky/typy-obsahu-údajů", null)
            // "Účastník provozu na pozemních komunikacích": NKOD dataset without a codelist → nothing
            // "Obecní úřad obce s rozšířenou působností": codelist is not an IRI → nothing
    );

    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ArchiReader archiReader;
    @Autowired
    private EnterpriseArchitectReader eaReader;
    @Autowired
    private OFNDataTransformerNew transformer;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ciselniky-test-excel.xlsx", "ciselniky-test-Archi.xml", "ciselniky-test-ea.xml"})
    void jsonLd_containsExpectedCodelists(String fixture) throws Exception {
        TransformationResult result = transformer.transform(read(fixture));
        JsonNode json = new ObjectMapper().readTree(transformer.exportToJson(result));

        Map<String, Codelist> actual = new HashMap<>();
        for (JsonNode concept : json.path("pojmy")) {
            JsonNode codelist = concept.get("instance-definovány-číselníkem");
            if (codelist == null) {
                continue;
            }
            assertEquals("Číselník", codelist.path("typ").asText(), "codelist typ");
            JsonNode dataset = codelist.get("datová-sada-v-nkod");
            actual.put(concept.path("název").path("cs").asText(),
                    new Codelist(codelist.path("iri").asText(), dataset == null ? null : dataset.asText()));
        }

        assertEquals(EXPECTED, actual);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ciselniky-test-excel.xlsx", "ciselniky-test-Archi.xml", "ciselniky-test-ea.xml"})
    void turtle_containsExpectedCodelists(String fixture) throws Exception {
        TransformationResult result = transformer.transform(read(fixture));
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(transformer.exportToTurtle(result), Lang.TURTLE).parse(model);

        Property hasCodelist = model.createProperty(SLOVNIKY + "má-instance-definované-číselníkem");
        Property hasDataset = model.createProperty(L111 + "má-v-nkod-zastřešující-datovou-sadu");
        Resource codelistType = model.createResource(L111 + "číselník");

        Map<String, Codelist> actual = new HashMap<>();
        model.listStatements(null, hasCodelist, (RDFNode) null).forEachRemaining(stmt -> {
            Resource codelist = stmt.getObject().asResource();
            assertTrue(model.contains(codelist, RDF.type, codelistType), codelist + " is typed číselník");
            Statement dataset = codelist.getProperty(hasDataset);
            actual.put(label(stmt.getSubject()),
                    new Codelist(codelist.getURI(), dataset == null ? null : dataset.getResource().getURI()));
        });

        assertEquals(EXPECTED, actual);

        Set<String> typedCodelists = model.listSubjectsWithProperty(RDF.type, codelistType).toSet().stream()
                .map(Resource::getURI).collect(Collectors.toSet());
        assertEquals(EXPECTED.values().stream().map(Codelist::iri).collect(Collectors.toSet()), typedCodelists,
                "only referenced, valid codelists are described");
    }

    private static String label(Resource concept) {
        return concept.listProperties(SKOS.prefLabel).toList().stream()
                .filter(s -> "cs".equals(s.getLanguage()))
                .map(Statement::getString)
                .findFirst()
                .orElse(concept.getURI());
    }

    private OntologyData read(String fixture) throws Exception {
        try (InputStream is = new ClassPathResource(FIXTURES + fixture).getInputStream()) {
            if (fixture.endsWith(".xlsx")) {
                return excelReader.readOntologyFromExcel(is);
            }
            byte[] bytes = is.readAllBytes();
            return fixture.contains("-ea")
                    ? eaReader.readXmiFromBytes(bytes)
                    : archiReader.readArchiFromString(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
