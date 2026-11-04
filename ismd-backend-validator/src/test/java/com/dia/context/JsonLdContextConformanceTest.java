package com.dia.context;

import com.dia.ofn.OfnContextIndex;
import com.dia.ofn.OfnContextIndex.Scope;
import com.dia.ofn.GateResult;
import com.dia.ofn.OfnConformanceGate;
import com.dia.ofn.OfnSpecification;
import com.dia.ofn.OfnContextIndex.Term;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks the JSON-LD export against the OFN slovníky context (kontext.jsonld):
 * <ul>
 *   <li>every key and every {@code typ} value is a term defined in the context,</li>
 *   <li>every value has the shape its term definition requires,</li>
 *   <li>the fixtures together exercise every term of the context,</li>
 *   <li>every output is valid against the official OFN JSON Schema (required fields, formats).</li>
 * </ul>
 * All rules are read from the context and schema files, so a new OFN version only needs those
 * files replaced (and {@code known-coverage-gaps.txt} updated) — no test code changes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("json")
@Tag("context")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonLdContextConformanceTest {

    private static final String KNOWN_GAPS_PATH = "com/dia/context/known-coverage-gaps.txt";
    private static final String FIXTURES = "com/dia/canonical/complete/";

    static List<String> fixtures() {
        return List.of(
                FIXTURES + "testExcelProject_no-lkod.xlsx",
                FIXTURES + "testArchiInput_no-lkod.xml",
                FIXTURES + "testEAInput_no-lkod.xml",
                FIXTURES + "ciselniky-test-excel.xlsx",
                FIXTURES + "ciselniky-test-Archi.xml",
                FIXTURES + "ciselniky-test-ea.xml"
        );
    }

    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ArchiReader archiReader;
    @Autowired
    private EnterpriseArchitectReader eaReader;
    @Autowired
    private OFNDataTransformer transformer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OfnContextIndex context;
    private JsonSchema schema;
    /** Concept variants of the schema (pojmy.items.anyOf), keyed by their title, e.g. "Třída". */
    private Map<String, JsonSchema> conceptSchemasByType;
    private final Map<String, JsonNode> outputs = new LinkedHashMap<>();

    @BeforeAll
    void setUp() throws Exception {
        OfnSpecification specification = OfnSpecification.bundled();
        context = specification.contextIndex();
        schema = specification.schema();
        conceptSchemasByType = specification.conceptSchemasByType();
        for (String fixture : fixtures()) {
            outputs.put(fixture, objectMapper.readTree(convert(fixture)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void output_usesOnlyContextTermsWithMatchingShapes(String fixture) {
        List<String> violations = new ArrayList<>();
        visitNode(outputs.get(fixture), initialScopes(), "", violations, new HashSet<>());

        if (!violations.isEmpty()) {
            fail(violations.size() + " context violation(s) in " + fixture + ":\n  "
                    + String.join("\n  ", violations));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void output_isValidAgainstOfnJsonSchema(String fixture) {
        JsonNode output = outputs.get(fixture);
        List<String> errors = new ArrayList<>(
                schema.validate(output).stream().map(ValidationMessage::getMessage).toList());

        // The schema lists concept variants in an open anyOf, so any concept passes as a plain "Pojem".
        // Its intent is that a concept of each type satisfies that type's variant: check that too.
        for (JsonNode concept : output.path("pojmy")) {
            for (JsonNode type : concept.path("typ")) {
                JsonSchema variant = conceptSchemasByType.get(type.asText());
                if (variant == null) {
                    continue;
                }
                variant.validate(concept).forEach(error -> errors.add(
                        "[" + concept.path("iri").asText() + " as " + type.asText() + "] " + error.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            fail(errors.size() + " OFN JSON Schema violation(s) in " + fixture + ":\n  "
                    + errors.stream().sorted().collect(Collectors.joining("\n  ")));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void output_passesConformanceGateUnchanged(String fixture) {
        JsonNode output = outputs.get(fixture);
        GateResult result = new OfnConformanceGate(OfnSpecification.bundled()).apply((ObjectNode) output);

        if (!result.issues().isEmpty()) {
            fail(result.issues().size() + " conformance gate issue(s) in " + fixture + ":\n  "
                    + result.issues().stream().map(Object::toString).collect(Collectors.joining("\n  ")));
        }
        assertEquals(output, result.document());
    }

    @Test
    void fixtures_coverEveryContextTerm() throws IOException {
        Set<String> used = new HashSet<>();
        for (JsonNode output : outputs.values()) {
            visitNode(output, initialScopes(), "", new ArrayList<>(), used);
        }
        Set<String> knownGaps = readKnownGaps();

        List<String> uncovered = context.allTerms().keySet().stream()
                .filter(path -> !used.contains(path) && !knownGaps.contains(path))
                .toList();
        List<String> staleGaps = knownGaps.stream()
                .filter(path -> used.contains(path) || !context.allTerms().containsKey(path))
                .sorted()
                .toList();

        StringBuilder message = new StringBuilder();
        if (!uncovered.isEmpty()) {
            message.append("Context terms not produced by any fixture (add fixture data or export support):\n  ")
                    .append(String.join("\n  ", uncovered)).append('\n');
        }
        if (!staleGaps.isEmpty()) {
            message.append("Remove from ").append(KNOWN_GAPS_PATH)
                    .append(" (now covered, or no longer in the context):\n  ")
                    .append(String.join("\n  ", staleGaps)).append('\n');
        }
        if (!message.isEmpty()) {
            fail(message.toString());
        }
    }

    // ---------------------------------------------------------------- traversal

    private Deque<Scope> initialScopes() {
        Deque<Scope> scopes = new ArrayDeque<>();
        scopes.push(context.root());
        return scopes;
    }

    /**
     * Walks one node object. Records used term paths and collects violations.
     * {@code scopes} holds the active contexts, innermost first.
     */
    private void visitNode(JsonNode node, Deque<Scope> scopes, String location,
                           List<String> violations, Set<String> used) {
        if (!node.isObject()) {
            violations.add(at(location) + "expected a node object, got " + node.getNodeType());
            return;
        }
        Deque<Scope> nodeScopes = applyTypeScopes(node, scopes, location, violations, used);

        node.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            String keyLocation = location.isEmpty() ? key : location + "/" + key;
            if (key.equals("@context")) {
                return;
            }
            Optional<Term> term = OfnContextIndex.resolve(nodeScopes, key);
            if (term.isEmpty()) {
                violations.add(at(keyLocation) + "key is not defined in the context");
                return;
            }
            used.add(term.get().path());
            checkValue(term.get(), value, nodeScopes, keyLocation, violations, used);
        });
    }

    /** Validates the node's {@code typ} values and activates their type-scoped contexts. */
    private Deque<Scope> applyTypeScopes(JsonNode node, Deque<Scope> scopes, String location,
                                         List<String> violations, Set<String> used) {
        JsonNode types = node.get("typ");
        if (types == null) {
            return scopes;
        }
        Deque<Scope> result = new ArrayDeque<>(scopes);
        for (JsonNode type : asList(types)) {
            if (!type.isTextual()) {
                violations.add(at(location + "/typ") + "type must be a string, got " + type.getNodeType());
                continue;
            }
            Optional<Term> typeTerm = OfnContextIndex.resolve(scopes, type.asText());
            if (typeTerm.isEmpty() || typeTerm.get().isNotTypeTerm()) {
                violations.add(at(location + "/typ") + "type '" + type.asText() + "' is not defined in the context");
                continue;
            }
            used.add(typeTerm.get().path());
            if (typeTerm.get().nested() != null) {
                result.push(typeTerm.get().nested());
            }
        }
        return result;
    }

    private void checkValue(Term term, JsonNode value, Deque<Scope> scopes, String location,
                            List<String> violations, Set<String> used) {
        if ("@id".equals(term.id())) {
            expectIri(value, location, violations);
            return;
        }
        if ("@type".equals(term.id())) {
            return; // checked in applyTypeScopes
        }

        String container = term.container();
        if ("@language".equals(container)) {
            checkLanguageMap(value, location, violations);
            return;
        }

        boolean isSet = "@set".equals(container) || term.isReverse();
        if (isSet && !value.isArray()) {
            violations.add(at(location) + "expected an array (@set container), got " + value.getNodeType());
        }
        if (!isSet && value.isArray()) {
            violations.add(at(location) + "expected a single value (no @set container), got an array");
        }

        Deque<Scope> valueScopes = scopes;
        if (term.nested() != null) {
            valueScopes = new ArrayDeque<>(scopes);
            valueScopes.push(term.nested());
        }
        int index = 0;
        for (JsonNode item : asList(value)) {
            String itemLocation = value.isArray() ? location + "[" + label(item, index++) + "]" : location;
            checkSingleValue(term, item, valueScopes, itemLocation, violations, used);
        }
    }

    private void checkSingleValue(Term term, JsonNode item, Deque<Scope> scopes, String location,
                                  List<String> violations, Set<String> used) {
        if (item.isObject()) {
            if (term.nested() == null && !term.isReverse()) {
                violations.add(at(location) + "term defines no nested context, but value is an object");
            }
            visitNode(item, scopes, location, violations, used);
            return;
        }
        String type = term.type();
        if ("@id".equals(type)) {
            expectIri(item, location, violations);
        } else if ("xsd:boolean".equals(type)) {
            if (!item.isBoolean()) {
                violations.add(at(location) + "expected a JSON boolean (xsd:boolean), got " + item.getNodeType());
            }
        } else if (type != null && type.startsWith("xsd:")) {
            if (!item.isTextual()) {
                violations.add(at(location) + "expected a string (" + type + "), got " + item.getNodeType());
            }
        } else if (term.isReverse() || term.nested() != null) {
            violations.add(at(location) + "expected a node object, got " + item.getNodeType());
        }
    }

    private void checkLanguageMap(JsonNode value, String location, List<String> violations) {
        if (!value.isObject()) {
            violations.add(at(location) + "expected a language map (@language container), got " + value.getNodeType());
            return;
        }
        value.properties().forEach(entry -> {
            if (!entry.getKey().matches("[a-zA-Z]{2,3}(-[a-zA-Z0-9]+)*")) {
                violations.add(at(location) + "'" + entry.getKey() + "' is not a language tag");
            }
            for (JsonNode text : asList(entry.getValue())) {
                if (!text.isTextual()) {
                    violations.add(at(location + "/" + entry.getKey()) + "expected a string, got " + text.getNodeType());
                }
            }
        });
    }

    private void expectIri(JsonNode value, String location, List<String> violations) {
        for (JsonNode item : asList(value)) {
            if (!item.isTextual() || !context.isIriOrKnownCurie(item.asText())) {
                violations.add(at(location) + "expected an IRI or a compact IRI with a context prefix, got " + item);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<JsonNode> asList(JsonNode value) {
        if (!value.isArray()) {
            return List.of(value);
        }
        List<JsonNode> items = new ArrayList<>();
        value.forEach(items::add);
        return items;
    }

    /** Last IRI segment of a node (e.g. "řidič"), or its array index when it has no IRI. */
    private static String label(JsonNode item, int index) {
        String iri = item.path("iri").asText("");
        return iri.isEmpty() ? String.valueOf(index) : iri.substring(iri.lastIndexOf('/') + 1);
    }

    private static String at(String location) {
        return "[" + (location.isEmpty() ? "/" : location) + "] ";
    }

    private String convert(String fixture) throws Exception {
        OntologyData data;
        if (fixture.endsWith(".xlsx")) {
            try (InputStream is = new ClassPathResource(fixture).getInputStream()) {
                data = excelReader.readOntologyFromExcel(is);
            }
        } else if (fixture.contains("EA") || fixture.contains("-ea")) {
            data = eaReader.readXmiFromBytes(readBytes(fixture));
        } else {
            data = archiReader.readArchiFromString(read(fixture));
        }
        TransformationResult result = transformer.transform(data);
        return transformer.exportToJson(result);
    }

    private Set<String> readKnownGaps() throws IOException {
        return read(KNOWN_GAPS_PATH).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String read(String path) throws IOException {
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return is.readAllBytes();
        }
    }
}
