package com.dia.exporter;

import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A digital object without a URL is described by název and popis (OFN slovníky tezaurus example).
 * No input format has a description field yet, so the test adds dcterms:description to a
 * converted model, as a future reader would, and checks both exports keep it.
 */
@SpringBootTest
@ActiveProfiles("test")
class DigitalObjectDescriptionExportTest {

    private static final String FIXTURE = "com/dia/canonical/complete/testExcelProject_no-lkod.xlsx";
    private static final String DIGITAL_OBJECT = "https://slovník.gov.cz/generický/digitální-objekty/pojem/digitální-objekt";
    private static final String TITLE = "Směrnice EU";
    private static final String DESCRIPTION = "Dokument k dispozici na podatelně, proto nemá URL";

    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private OFNDataTransformerNew transformer;

    @Test
    void descriptionOfDigitalObject_isExportedToJsonLdAndTurtle() throws Exception {
        TransformationResult result;
        try (InputStream is = new ClassPathResource(FIXTURE).getInputStream()) {
            result = transformer.transform(excelReader.readOntologyFromExcel(is));
        }
        int described = addDescriptionToTitledDocuments(result.getOntModel());
        assertTrue(described > 0, "fixture should contain a digital object titled '" + TITLE + "'");

        assertJsonLdHasDescription(transformer.exportToJson(result), described);
        assertTurtleHasDescription(transformer.exportToTurtle(result), described);
    }

    private int addDescriptionToTitledDocuments(Model model) {
        List<Resource> documents = new ArrayList<>();
        model.listSubjectsWithProperty(RDF.type, model.createResource(DIGITAL_OBJECT)).forEachRemaining(doc -> {
            if (doc.hasProperty(DCTerms.title, model.createLiteral(TITLE, "cs"))) {
                documents.add(doc);
            }
        });
        documents.forEach(doc -> doc.addProperty(DCTerms.description, DESCRIPTION, "cs"));
        return documents.size();
    }

    private void assertJsonLdHasDescription(String json, int expectedCount) throws Exception {
        int found = 0;
        for (JsonNode concept : new ObjectMapper().readTree(json).path("pojmy")) {
            for (String key : List.of("definující-nelegislativní-zdroj", "související-nelegislativní-zdroj")) {
                for (JsonNode doc : concept.path(key)) {
                    if (TITLE.equals(doc.path("název").path("cs").asText())) {
                        assertEquals(DESCRIPTION, doc.path("popis").path("cs").asText(),
                                "popis of '" + TITLE + "' in " + concept.path("iri").asText());
                        found++;
                    }
                }
            }
        }
        assertEquals(expectedCount, found, "every described document appears in JSON-LD");
    }

    private void assertTurtleHasDescription(String turtle, int expectedCount) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(turtle, Lang.TURTLE).parse(model);

        long described = model.listSubjectsWithProperty(DCTerms.description, model.createLiteral(DESCRIPTION, "cs"))
                .filterKeep(doc -> doc.hasProperty(RDF.type, model.createResource(DIGITAL_OBJECT)))
                .filterKeep(doc -> doc.hasProperty(DCTerms.title, model.createLiteral(TITLE, "cs")))
                .toList().size();
        assertEquals(expectedCount, described, "every described document appears in Turtle");
    }
}
