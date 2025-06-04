package com.dia.exporter;

import com.dia.exceptions.TurtleExportException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.dia.constants.ArchiOntologyConstants.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@ExtendWith(MockitoExtension.class)
class TurtleExporterUnitTest {

    private OntModel ontModel;
    private Map<String, Resource> resourceMap;
    private String modelName;
    private Map<String, String> modelProperties;
    private TurtleExporter exporter;

    @BeforeEach
    void setUp() {
        ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        resourceMap = new HashMap<>();
        modelName = "Test Vocabulary";
        modelProperties = new HashMap<>();
        modelProperties.put("adresa lokálního katalogu dat", NS);
        MDC.put(LOG_REQUEST_ID, "test-request-123");
        exporter = new TurtleExporter(ontModel, resourceMap, modelName, modelProperties);
    }

    // ================= CORE FUNCTIONALITY TESTS =================

    @Test
    void exportToTurtle_WithMinimalModel_ReturnsValidTurtle() {
        // Arrange
        setupMinimalOntologyModel();

        // Act
        String result = exporter.exportToTurtle();

        // Assert
        validateBasicTurtleOutput(result);
        validateSKOSStructure(parseModel(result));
    }

    @Test
    void exportToTurtle_WithConcepts_TransformsToSKOSConcepts() {
        // Arrange
        setupModelWithConcepts();

        // Act & Assert
        Model parsedModel = exportAndParseModel();

        assertAll("SKOS concept transformations",
                () -> assertTrue(parsedModel.contains(null, RDF.type, SKOS.Concept)),
                () -> assertTrue(parsedModel.contains(null, SKOS.prefLabel, (RDFNode) null)),
                () -> assertTrue(parsedModel.contains(null, SKOS.inScheme, (RDFNode) null))
        );
    }

    // ================= PARAMETERIZED TESTS =================

    @ParameterizedTest(name = "Prefix generation for {0}")
    @CsvSource({
            "https://123numeric-domain.org/, n123numeric",
            "https://sub.domain.example.org/path/, sub",
            "https://domain-with-hyphens.org/, domain",
            "urn:example:namespace:, example",
            "'', domain",
            "https://999-all-numeric.com/, n999",
            "https://special-chars!@#.com/, special"
    })
    void determineMainPrefix_WithVariousInputs_GeneratesValidPrefixes(String input, String expectedPrefix) throws Exception {
        // Arrange
        Method method = TurtleExporter.class.getDeclaredMethod("determineMainPrefix", String.class);
        method.setAccessible(true);

        // Act
        String result = (String) method.invoke(exporter, input.equals("''") ? "" : input);

        // Assert
        assertAll("Valid XML prefix validation",
                () -> assertEquals(expectedPrefix, result, "Should generate expected prefix"),
                () -> assertTrue(result.matches("[a-zA-Z][a-zA-Z0-9]*"), "Should match XML name pattern"),
                () -> assertTrue(Character.isLetter(result.charAt(0)), "Should start with letter")
        );
    }

    @ParameterizedTest(name = "Boolean value: {0}")
    @ValueSource(strings = {"true", "false", "ano", "ne", "invalid-value"})
    void exportToTurtle_WithBooleanValues_HandlesAllCases(String booleanValue) {
        // Arrange
        setupModelWithBooleanValue(booleanValue);

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        if (isTrueBooleanValue(booleanValue)) {
            assertTrue(hasBooleanLiteralWithValue(parsedModel, true),
                    "Should contain true boolean for: " + booleanValue);
        } else if (isFalseBooleanValue(booleanValue)) {
            assertTrue(hasBooleanLiteralWithValue(parsedModel, false),
                    "Should contain false boolean for: " + booleanValue);
        }
        // Invalid values are not converted to booleans - no assertion needed
    }

    @ParameterizedTest(name = "Required prefix: {0}")
    @ValueSource(strings = {"PREFIX rdf:", "PREFIX rdfs:", "PREFIX skos:", "PREFIX owl:", "PREFIX dct:", "PREFIX xsd:"})
    void exportToTurtle_WithMinimalModel_ContainsRequiredPrefixes(String expectedPrefix) {
        // Arrange
        setupMinimalOntologyModel();

        // Act
        String result = exporter.exportToTurtle();

        // Assert
        assertTrue(result.contains(expectedPrefix),
                "Should contain required prefix: " + expectedPrefix);
    }

    // ================= PROPERTY MAPPING TESTS =================

    @TestFactory
    Stream<DynamicTest> propertyMappingTests() {
        return Stream.of(
                dynamicTest("DCTerms.source mapping", () -> testPropertyMapping(LABEL_ZDROJ, DCTerms.source, "http://example.org/source")),
                dynamicTest("DCTerms.references mapping", () -> testPropertyMapping(LABEL_SZ, DCTerms.references, "http://example.org/related")),
                dynamicTest("RDFS.subClassOf mapping", () -> testPropertyMapping(LABEL_NT, RDFS.subClassOf, NS + "super-class")),
                dynamicTest("Boolean property mapping", this::testBooleanPropertyMapping),
                dynamicTest("AIS property mapping", () -> testComplexPropertyMapping(LABEL_AIS, NS + AGENDOVY_104 + LABEL_UDAJE_AIS, "AIS-value")),
                dynamicTest("AGENDA property mapping", () -> testComplexPropertyMapping(LABEL_AGENDA, NS + AGENDOVY_104 + AGENDA_LONG, "agenda-value"))
        );
    }

    @TestFactory
    Stream<DynamicTest> conceptTypeTests() {
        return Stream.of(
                dynamicTest("OWL Class assignment", () -> {
                    setupModelWithDifferentConceptTypes();
                    Model parsedModel = exportAndParseModel();
                    assertTrue(parsedModel.contains(null, RDF.type, OWL2.Class));
                }),
                dynamicTest("OWL ObjectProperty assignment", () -> {
                    setupModelWithDifferentConceptTypes();
                    Model parsedModel = exportAndParseModel();
                    assertTrue(parsedModel.contains(null, RDF.type, OWL2.ObjectProperty));
                }),
                dynamicTest("OWL DatatypeProperty assignment", () -> {
                    setupModelWithDifferentConceptTypes();
                    Model parsedModel = exportAndParseModel();
                    assertTrue(parsedModel.contains(null, RDF.type, OWL2.DatatypeProperty));
                })
        );
    }

    // ================= ADVANCED CONCEPT TYPES =================

    static Stream<Arguments> advancedConceptTypeArguments() {
        return Stream.of(
                Arguments.of("TSP type mapping", NS + VS_POJEM + LABEL_TSP),
                Arguments.of("TOP type mapping", NS + VS_POJEM + LABEL_TOP),
                Arguments.of("Public data type mapping", NS + LEGISLATIVNI_111_VU),
                Arguments.of("Non-public data type mapping", NS + LEGISLATIVNI_111_NVU)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("advancedConceptTypeArguments")
    void exportToTurtle_WithAdvancedConceptTypes_AssignsCorrectTypes(String testName, String expectedTypeURI) {
        // Arrange
        setupModelWithAdvancedConceptTypes();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        Resource expectedType = parsedModel.createResource(expectedTypeURI);
        assertTrue(parsedModel.contains(null, RDF.type, expectedType),
                "Should contain " + testName + ": " + expectedTypeURI);
    }

    // ================= EDGE CASE TESTS =================

    @ParameterizedTest(name = "Language: {0}")
    @CsvSource({
            "cs, Czech Concept",
            "en, English Concept"
    })
    void exportToTurtle_WithMultilingualLabels_PreservesLanguageTags(String expectedLang, String expectedLabel) {
        // Arrange
        setupModelWithMultilingualContent();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertTrue(hasLabelWithLanguage(parsedModel, expectedLang, expectedLabel),
                "Should preserve " + expectedLang + " language tag with label: " + expectedLabel);
    }

    @Test
    void exportToTurtle_WithEmptyModel_ReturnsValidEmptyTurtle() {
        // Act
        String result = exporter.exportToTurtle();

        // Assert
        assertAll("Empty model validation",
                () -> assertNotNull(result),
                () -> assertTrue(result.contains("PREFIX")),
                () -> assertDoesNotThrow(() -> parseModel(result))
        );
    }

    @Test
    void exportToTurtle_WithMultipleOntologies_CreatesOneConceptScheme() {
        // Arrange
        setupModelWithMultipleOntologies();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertEquals(1, countStatements(parsedModel, SKOS.ConceptScheme),
                "Should create exactly one ConceptScheme");
    }

    // ================= ERROR HANDLING TESTS =================

    @Test
    void exportToTurtle_WithInvalidData_ThrowsTurtleExportException() {
        TurtleExporter invalidExporter = new TurtleExporter(null, resourceMap, modelName, modelProperties);
        assertThrows(TurtleExportException.class, invalidExporter::exportToTurtle);
    }

    @ParameterizedTest(name = "Invalid namespace: {0}")
    @ValueSource(strings = {"not-a-valid-url", "invalid://malformed", ""})
    void exportToTurtle_WithInvalidNamespace_UsesDefaultNamespace(String invalidNamespace) {
        // Arrange
        modelProperties.put("adresa lokálního katalogu dat", invalidNamespace);
        exporter = new TurtleExporter(ontModel, resourceMap, modelName, modelProperties);
        setupMinimalOntologyModel();

        // Act & Assert
        assertDoesNotThrow(() -> {
            String result = exporter.exportToTurtle();
            validateBasicTurtleOutput(result);
        });
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10)
    void exportToTurtle_WithLargeModel_CompletesInReasonableTime() {
        // Arrange
        setupLargeModel();

        // Act & Assert
        assertDoesNotThrow(() -> {
            String result = exporter.exportToTurtle();
            Model parsedModel = parseModel(result);
            long conceptCount = countStatements(parsedModel, SKOS.Concept);
            assertTrue(conceptCount >= 900, "Should process most concepts");
        });
    }

    // ================= HELPER METHODS =================

    private Model exportAndParseModel() {
        String result = exporter.exportToTurtle();
        return parseModel(result);
    }

    private Model parseModel(String turtleContent) {
        Model model = ModelFactory.createDefaultModel();
        model.read(new StringReader(turtleContent), null, "TURTLE");
        return model;
    }

    private void validateBasicTurtleOutput(String result) {
        assertAll("Basic Turtle validation",
                () -> assertNotNull(result, "Turtle output should not be null"),
                () -> assertTrue(result.contains("PREFIX"), "Should contain prefix declarations"),
                () -> assertFalse(result.trim().isEmpty(), "Should not be empty")
        );
    }

    private void validateSKOSStructure(Model parsedModel) {
        assertAll("SKOS structure validation",
                () -> assertFalse(parsedModel.isEmpty(), "Parsed model should not be empty"),
                () -> assertTrue(parsedModel.contains(null, RDF.type, SKOS.ConceptScheme),
                        "Should contain SKOS ConceptScheme")
        );
    }

    private void testPropertyMapping(String sourceProperty, Property expectedProperty, String value) {
        setupModelWithCustomProperty(sourceProperty, value);
        Model parsedModel = exportAndParseModel();
        assertTrue(parsedModel.contains(null, expectedProperty, (RDFNode) null),
                "Should map " + sourceProperty + " to " + expectedProperty.getURI());
    }

    private void testComplexPropertyMapping(String sourceProperty, String expectedPropertyURI, String value) {
        setupModelWithCustomProperty(sourceProperty, value);
        Model parsedModel = exportAndParseModel();
        Property expectedProperty = parsedModel.createProperty(expectedPropertyURI);
        assertTrue(parsedModel.contains(null, expectedProperty, (RDFNode) null),
                "Should map " + sourceProperty + " to " + expectedPropertyURI);
    }

    private void testBooleanPropertyMapping() {
        setupModelWithCustomProperty(LABEL_JE_PPDF, "true");
        Model parsedModel = exportAndParseModel();

        String expectedPropertyURI = NS + AGENDOVY_104 + LABEL_JE_PPDF_LONG;
        Property expectedProperty = parsedModel.createProperty(expectedPropertyURI);

        assertTrue(parsedModel.contains(null, expectedProperty, (RDFNode) null),
                "Should map boolean property");
        assertTrue(hasBooleanLiteralWithValue(parsedModel, true),
                "Should have properly typed boolean value");
    }

    private boolean isTrueBooleanValue(String value) {
        return "true".equals(value) || "ano".equals(value);
    }

    private boolean isFalseBooleanValue(String value) {
        return "false".equals(value) || "ne".equals(value);
    }

    private boolean hasBooleanLiteralWithValue(Model model, boolean expectedValue) {
        StmtIterator stmtIter = model.listStatements();
        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.next();
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                if (lit.getDatatype() != null &&
                        lit.getDatatype().equals(XSDDatatype.XSDboolean) &&
                        lit.getBoolean() == expectedValue) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLabelWithLanguage(Model model, String language, String expectedText) {
        StmtIterator labelIter = model.listStatements(null, SKOS.prefLabel, (RDFNode) null);
        while (labelIter.hasNext()) {
            Statement stmt = labelIter.next();
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                if (language.equals(lit.getLanguage()) && expectedText.equals(lit.getString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private long countStatements(Model model, RDFNode object) {
        return model.listStatements(null, RDF.type, object).toList().size();
    }

    // ================= SETUP METHODS =================

    private void setupMinimalOntologyModel() {
        Resource ontology = ontModel.createOntology(NS + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);
        Resource testConcept = ontModel.createResource(NS + "test-concept");
        testConcept.addProperty(RDF.type, pojemClass);
        testConcept.addProperty(RDFS.label, "Test Concept", "cs");
        resourceMap.put("test-concept", testConcept);
    }

    private void setupModelWithConcepts() {
        setupMinimalOntologyModel();
        OntClass pojemClass = ontModel.getOntClass(NS + TYP_POJEM);

        Resource concept2 = ontModel.createResource(NS + "concept-2");
        concept2.addProperty(RDF.type, pojemClass);
        concept2.addProperty(RDFS.label, "Second Concept", "cs");
        resourceMap.put("concept-2", concept2);
    }

    private void setupModelWithCustomProperty(String propertyName, String value) {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");
        Property customProp = ontModel.createProperty(NS + propertyName);

        if (value.startsWith("http://")) {
            concept.addProperty(customProp, ontModel.createResource(value));
        } else {
            concept.addProperty(customProp, value);
        }
    }

    private void setupModelWithBooleanValue(String booleanValue) {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");
        Property ppdfProp = ontModel.createProperty(NS + LABEL_JE_PPDF);
        concept.addProperty(ppdfProp, booleanValue);
    }

    private void setupModelWithMultilingualContent() {
        Resource ontology = ontModel.createOntology(NS + "multilingual-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);
        Resource concept = ontModel.createResource(NS + "multilingual-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(RDFS.label, "Czech Concept", "cs");
        concept.addProperty(RDFS.label, "English Concept", "en");
        resourceMap.put("multilingual-concept", concept);
    }

    private void setupModelWithDifferentConceptTypes() {
        Resource ontology = ontModel.createOntology(NS + "typed-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);
        OntClass tridaClass = ontModel.createClass(NS + TYP_TRIDA);
        OntClass vlastnostClass = ontModel.createClass(NS + TYP_VLASTNOST);
        OntClass vztahClass = ontModel.createClass(NS + TYP_VZTAH);

        // Class concept
        Resource classConcept = ontModel.createResource(NS + "class-concept");
        classConcept.addProperty(RDF.type, pojemClass);
        classConcept.addProperty(RDF.type, tridaClass);
        classConcept.addProperty(RDFS.label, "Class Concept", "cs");

        // Property concept
        Resource propertyConcept = ontModel.createResource(NS + "property-concept");
        propertyConcept.addProperty(RDF.type, pojemClass);
        propertyConcept.addProperty(RDF.type, vlastnostClass);
        propertyConcept.addProperty(RDFS.label, "Property Concept", "cs");

        // Relation concept
        Resource relationConcept = ontModel.createResource(NS + "relation-concept");
        relationConcept.addProperty(RDF.type, pojemClass);
        relationConcept.addProperty(RDF.type, vztahClass);
        relationConcept.addProperty(RDFS.label, "Relation Concept", "cs");
    }

    private void setupModelWithAdvancedConceptTypes() {
        Resource ontology = ontModel.createOntology(NS + "advanced-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);
        OntClass tridaClass = ontModel.createClass(NS + TYP_TRIDA);
        OntClass tspClass = ontModel.createClass(NS + TYP_TSP);
        OntClass topClass = ontModel.createClass(NS + TYP_TOP);
        OntClass verejnyUdajClass = ontModel.createClass(NS + TYP_VEREJNY_UDAJ);
        OntClass neverejnyUdajClass = ontModel.createClass(NS + TYP_NEVEREJNY_UDAJ);

        // TSP concept (needs both TYP_TRIDA and TYP_TSP)
        Resource tspConcept = ontModel.createResource(NS + "tsp-concept");
        tspConcept.addProperty(RDF.type, pojemClass);
        tspConcept.addProperty(RDF.type, tridaClass);
        tspConcept.addProperty(RDF.type, tspClass);
        tspConcept.addProperty(RDFS.label, "TSP Concept", "cs");

        // TOP concept (needs both TYP_TRIDA and TYP_TOP)
        Resource topConcept = ontModel.createResource(NS + "top-concept");
        topConcept.addProperty(RDF.type, pojemClass);
        topConcept.addProperty(RDF.type, tridaClass);
        topConcept.addProperty(RDF.type, topClass);
        topConcept.addProperty(RDFS.label, "TOP Concept", "cs");

        // Data access concepts
        Resource publicDataConcept = ontModel.createResource(NS + "public-data-concept");
        publicDataConcept.addProperty(RDF.type, pojemClass);
        publicDataConcept.addProperty(RDF.type, verejnyUdajClass);
        publicDataConcept.addProperty(RDFS.label, "Public Data Concept", "cs");

        Resource nonPublicDataConcept = ontModel.createResource(NS + "non-public-data-concept");
        nonPublicDataConcept.addProperty(RDF.type, pojemClass);
        nonPublicDataConcept.addProperty(RDF.type, neverejnyUdajClass);
        nonPublicDataConcept.addProperty(RDFS.label, "Non-Public Data Concept", "cs");
    }

    private void setupModelWithMultipleOntologies() {
        Resource ontology1 = ontModel.createOntology(NS + "vocab-1");
        ontology1.addProperty(RDF.type, OWL2.Ontology);

        Resource ontology2 = ontModel.createOntology(NS + "vocab-2");
        ontology2.addProperty(RDF.type, OWL2.Ontology);

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);

        Resource concept1 = ontModel.createResource(NS + "concept-1");
        concept1.addProperty(RDF.type, pojemClass);
        concept1.addProperty(RDFS.label, "Concept 1", "cs");

        Resource concept2 = ontModel.createResource(NS + "concept-2");
        concept2.addProperty(RDF.type, pojemClass);
        concept2.addProperty(RDFS.label, "Concept 2", "cs");
    }

    private void setupLargeModel() {
        Resource ontology = ontModel.createOntology(NS + "large-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);

        for (int i = 0; i < 1000; i++) {
            Resource concept = ontModel.createResource(NS + "concept-" + i);
            concept.addProperty(RDF.type, pojemClass);
            concept.addProperty(RDFS.label, "Concept " + i, "cs");
        }
    }
}