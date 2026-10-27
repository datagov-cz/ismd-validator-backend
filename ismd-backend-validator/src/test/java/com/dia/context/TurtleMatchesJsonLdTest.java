package com.dia.context;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The JSON-LD and Turtle exports must describe the same graph: expanding the JSON-LD output
 * with the OFN context yields exactly the triples of the Turtle output (up to blank node IDs).
 */
@SpringBootTest
@ActiveProfiles("test")
class TurtleMatchesJsonLdTest {

    private static final String CONTEXT_PATH = "com/dia/context/json_ld_context.jsonld";

    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ArchiReader archiReader;
    @Autowired
    private EnterpriseArchitectReader eaReader;
    @Autowired
    private OFNDataTransformerNew transformer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    static List<String> fixtures() {
        return JsonLdContextConformanceTest.fixtures();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void expandedJsonLd_isIsomorphicWithTurtle(String fixture) throws Exception {
        TransformationResult result = transformer.transform(read(fixture));

        Model fromJsonLd = expandWithLocalContext(transformer.exportToJson(result));
        Model fromTurtle = ModelFactory.createDefaultModel();
        RDFParser.fromString(transformer.exportToTurtle(result), Lang.TURTLE).parse(fromTurtle);

        if (!fromJsonLd.isIsomorphicWith(fromTurtle)) {
            fail("JSON-LD and Turtle exports of " + fixture + " differ:\n"
                    + describe("only in JSON-LD", fromJsonLd.difference(fromTurtle))
                    + describe("only in Turtle", fromTurtle.difference(fromJsonLd)));
        }
    }

    /** Parses the JSON-LD with the bundled context inlined, so no network fetch is needed. */
    private Model expandWithLocalContext(String json) throws Exception {
        ObjectNode document = (ObjectNode) objectMapper.readTree(json);
        document.set("@context", objectMapper.readTree(read(CONTEXT_PATH, true)).get("@context"));
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(objectMapper.writeValueAsString(document), Lang.JSONLD).parse(model);
        return model;
    }

    /**
     * Lists differing statements without blank nodes first; statements on blank nodes (dates, digital
     * objects) appear on both sides whenever anything in their subtree differs, so they are only counted.
     */
    private static String describe(String title, Model statements) {
        List<Statement> all = statements.listStatements().toList();
        List<String> named = all.stream()
                .filter(s -> !s.getSubject().isAnon() && !s.getObject().isAnon())
                .map(Statement::toString)
                .sorted()
                .toList();
        return "  " + title + ": " + named.size() + " without blank nodes, "
                + (all.size() - named.size()) + " on blank nodes\n"
                + named.stream().limit(40).map(line -> "    " + line).collect(Collectors.joining("\n")) + "\n";
    }

    private OntologyData read(String fixture) throws Exception {
        try (InputStream is = new ClassPathResource(fixture).getInputStream()) {
            if (fixture.endsWith(".xlsx")) {
                return excelReader.readOntologyFromExcel(is);
            }
            byte[] bytes = is.readAllBytes();
            return fixture.contains("EA") || fixture.contains("-ea")
                    ? eaReader.readXmiFromBytes(bytes)
                    : archiReader.readArchiFromString(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static String read(String path, boolean text) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
