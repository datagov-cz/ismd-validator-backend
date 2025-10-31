package com.dia.exporter;

import com.dia.exceptions.TurtleExportException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;
import static com.dia.constants.VocabularyConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Test class for {@link TurtleExporter}.
 */
@ExtendWith(MockitoExtension.class)
class TurtleExporterUnitTest {

    private OntModel ontModel;
    private Map<String, Resource> resourceMap;
    private String modelName;
    private Map<String, String> modelProperties;
    private String effectiveNamespace;
    private TurtleExporter exporter;

    @BeforeEach
    void setUp() {
        ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        resourceMap = new HashMap<>();
        modelName = "Test Vocabulary";
        modelProperties = new HashMap<>();

        // Use a specific vocabulary namespace that won't be filtered
        effectiveNamespace = "https://slovník.gov.cz/legislativní/sbírka/test/2024/pojem/";

        modelProperties.put(LOKALNI_KATALOG, effectiveNamespace);
        MDC.put(LOG_REQUEST_ID, "test-request-123");
        exporter = new TurtleExporter(ontModel, resourceMap, modelName, modelProperties, effectiveNamespace);
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

    /*
    @Test
    void exportToTurtle_WithConcepts_TransformsToSKOSConcepts() {
        // Arrange
        setupModelWithConcepts();

        // Act & Assert
        Model parsedModel = exportAndParseModel();

        assertAll("SKOS concept transformations",
                () -> assertTrue(parsedModel.contains(null, RDF.type, SKOS.Concept),
                        "Should transform concepts to SKOS Concepts"),
                () -> assertTrue(parsedModel.contains(null, SKOS.prefLabel, (RDFNode) null),
                        "Should transform labels to SKOS prefLabel"),
                () -> assertTrue(parsedModel.contains(null, SKOS.inScheme, (RDFNode) null),
                        "Should add inScheme relationships")
        );
    }

     */

    @Test
    void exportToTurtle_WithConceptScheme_CreatesProperConceptScheme() {
        // Arrange
        setupMinimalOntologyModel();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertAll("ConceptScheme validation",
                () -> assertTrue(parsedModel.contains(null, RDF.type, SKOS.ConceptScheme),
                        "Should create SKOS ConceptScheme"),
                () -> assertTrue(parsedModel.contains(null, RDF.type, OWL2.Ontology),
                        "Should maintain OWL Ontology type"),
                () -> assertTrue(hasConceptSchemeWithCorrectTypes(parsedModel),
                        "Should have correct vocabulary types")
        );
    }

    @Test
    void exportToTurtle_WithEmptyLiterals_FiltersOutEmptyValues() {
        // Arrange
        setupModelWithEmptyLiterals();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertFalse(hasEmptyLiterals(parsedModel), "Should filter out empty literal values");
    }

    @Test
    void exportToTurtle_WithDefinitions_TransformsToSKOSDefinitions() {
        // Arrange
        setupModelWithDefinitions();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertAll("Definition transformation",
                () -> assertTrue(parsedModel.contains(null, SKOS.definition, (RDFNode) null),
                        "Should transform definitions to SKOS definition"),
                () -> assertFalse(hasCustomDefinitionProperty(parsedModel),
                        "Should remove custom definition properties")
        );
    }

    // ================= PROPERTY MAPPING TESTS =================

    @TestFactory
    Stream<DynamicTest> propertyMappingTests() {
        return Stream.of(
                dynamicTest("Boolean property mapping (JE_PPDF)", this::testBooleanPropertyMapping),
                dynamicTest("AIS property mapping", () -> testComplexPropertyMapping(AIS, DEFAULT_NS + AGENDOVY_104 + UDAJE_AIS, "AIS-value")),
                dynamicTest("AGENDA property mapping", () -> testComplexPropertyMapping(AGENDA, DEFAULT_NS + AGENDOVY_104 + AGENDA_LONG, "agenda-value")),
                dynamicTest("USTANOVENI property mapping", () -> testComplexPropertyMapping(USTANOVENI_NEVEREJNOST, DEFAULT_NS + LEGISLATIVNI_111 + USTANOVENI_LONG, "legal-provision")),
                dynamicTest("Domain property mapping", this::testDomainPropertyMapping),
                dynamicTest("Range property mapping", this::testRangePropertyMapping)
        );
    }

    @Test
    void exportToTurtle_WithDomainAndRangeProperties_TransformsToRDFS() {
        // Arrange
        setupModelWithDomainAndRange();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertAll("Domain and Range transformations",
                () -> assertTrue(parsedModel.contains(null, RDFS.domain, (RDFNode) null),
                        "Should transform custom domain to RDFS domain"),
                () -> assertTrue(parsedModel.contains(null, RDFS.range, (RDFNode) null),
                        "Should transform custom range to RDFS range"),
                () -> assertFalse(hasCustomDomainProperty(parsedModel),
                        "Should remove custom domain properties"),
                () -> assertFalse(hasCustomRangeProperty(parsedModel),
                        "Should remove custom range properties")
        );
    }

    // ================= PARAMETERIZED TESTS =================

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

    @ParameterizedTest(name = "Empty value type: {0}")
    @ValueSource(strings = {"", "   ", "\t", "\n", "null"})
    void exportToTurtle_WithVariousEmptyValues_FiltersCorrectly(String emptyValue) {
        // Arrange
        setupModelWithSpecificEmptyValue(emptyValue.equals("null") ? null : emptyValue);

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertFalse(hasEmptyLiterals(parsedModel),
                "Should filter out empty value: '" + emptyValue + "'");
    }

    // ================= ADVANCED CONCEPT TYPES =================

    /*
    @TestFactory
    Stream<DynamicTest> conceptTypeTests() {
        return Stream.of(
                dynamicTest("OFN Pojem to SKOS Concept transformation", () -> {
                    setupModelWithOFNConcepts();
                    Model parsedModel = exportAndParseModel();
                    assertTrue(parsedModel.contains(null, RDF.type, SKOS.Concept),
                            "Should transform OFN pojmy to SKOS Concepts");
                }),
                dynamicTest("Multiple concept types handling", () -> {
                    setupModelWithDifferentConceptTypes();
                    Model parsedModel = exportAndParseModel();
                    assertTrue(parsedModel.contains(null, RDF.type, SKOS.Concept),
                            "Should handle multiple concept types");
                }),
                dynamicTest("InScheme relationships creation", () -> {
                    setupModelWithConcepts();
                    Model parsedModel = exportAndParseModel();
                    assertTrue(parsedModel.contains(null, SKOS.inScheme, (RDFNode) null),
                            "Should create inScheme relationships");
                })
        );
    }

     */

    // ================= BASE SCHEMA FILTERING TESTS =================

    @Test
    void exportToTurtle_WithBaseSchemaResources_FiltersCorrectly() {
        // Arrange
        setupModelWithBaseSchemaResources();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertAll("Base schema filtering",
                () -> assertFalse(hasBaseSchemaStatements(parsedModel),
                        "Should filter out XSD schema statements"),
                () -> assertFalse(hasBaseSchemaConceptsWithSKOSType(parsedModel),
                        "Should not convert base schema classes to SKOS concepts"),
                () -> assertFalse(hasXSDSchemaStatements(parsedModel),
                        "Should filter out XSD schema statements")
        );
    }

    @Test
    void isBaseSchemaResource_WithVariousURIs_FiltersCorrectly() throws Exception {
        // Test the isBaseSchemaResource helper method via reflection
        Method method = TurtleExporter.class.getDeclaredMethod("isBaseSchemaResource", String.class);
        method.setAccessible(true);

        // Test XSD URIs
        assertTrue((Boolean) method.invoke(exporter, "http://www.w3.org/2001/XMLSchema#string"),
                "Should filter XSD schema URIs");

        // Test government vocabulary URIs that should be filtered (note the trailing slash)
        assertTrue((Boolean) method.invoke(exporter, "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/"),
                "Should filter generic OFN schema URIs");

        // Test government vocabulary URIs that should NOT be filtered
        assertFalse((Boolean) method.invoke(exporter, "https://slovník.gov.cz/legislativní/sbírka/111/2009/pojem/test"),
                "Should NOT filter legislative URIs");
        assertFalse((Boolean) method.invoke(exporter, "https://slovník.gov.cz/agendový/104/pojem/test"),
                "Should NOT filter agenda URIs");
        assertFalse((Boolean) method.invoke(exporter, "https://slovník.gov.cz/veřejný-sektor/pojem/test"),
                "Should NOT filter public sector URIs");
        
    }

    // ================= EDGE CASE TESTS =================

    @Test
    void exportToTurtle_WithMultipleOntologies_HandlesCorrectly() {
        // Arrange
        setupModelWithMultipleOntologies();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertTrue(parsedModel.contains(null, RDF.type, SKOS.ConceptScheme),
                "Should handle multiple ontologies and create ConceptScheme");
    }

    @Test
    void exportToTurtle_WithMissingOntologyIRI_HandlesGracefully() {
        // Arrange
        setupModelWithoutOntologyIRI();

        // Act & Assert
        assertDoesNotThrow(() -> {
            String result = exporter.exportToTurtle();
            assertNotNull(result, "Should produce result even without ontology IRI");
        });
    }

    @Test
    void exportToTurtle_WithModelMetadata_IncludesInConceptScheme() {
        // Arrange
        modelProperties.put(POPIS, "Test description");
        exporter = new TurtleExporter(ontModel, resourceMap, modelName, modelProperties, effectiveNamespace);
        setupMinimalOntologyModel();

        // Act
        Model parsedModel = exportAndParseModel();

        // Assert
        assertAll("Model metadata handling",
                () -> assertTrue(parsedModel.contains(null, SKOS.prefLabel, (RDFNode) null),
                        "Should include model name as SKOS prefLabel"),
                () -> assertTrue(parsedModel.contains(null, DCTerms.description, (RDFNode) null),
                        "Should include description as DCTerms description")
        );
    }

    @Test
    void exportToTurtle_WithOntologyFromDifferentSources_FindsCorrectIRI() {
        // Test getOntologyIRI method's different sources

        // Test 1: From resource map
        setupMinimalOntologyModel();
        String result1 = exporter.exportToTurtle();
        assertNotNull(result1, "Should work with ontology from resource map");

        // Test 2: From model statements (clear resource map)
        resourceMap.clear();
        exporter = new TurtleExporter(ontModel, resourceMap, modelName, modelProperties, effectiveNamespace);
        String result2 = exporter.exportToTurtle();
        assertNotNull(result2, "Should work with ontology from model statements");

        // Test 3: From catalog namespace (clear ontology statements)
        ontModel.removeAll(null, RDF.type, OWL2.Ontology);
        String result3 = exporter.exportToTurtle();
        assertNotNull(result3, "Should work with catalog namespace fallback");
    }

    // ================= ERROR HANDLING TESTS =================

    @Test
    void exportToTurtle_WithNullModel_ThrowsTurtleExportException() {
        // Arrange
        TurtleExporter invalidExporter = new TurtleExporter(null, resourceMap, modelName, modelProperties, effectiveNamespace);

        // Act & Assert
        TurtleExportException exception = assertThrows(TurtleExportException.class,
                invalidExporter::exportToTurtle,
                "Should throw TurtleExportException for null model");

        assertTrue(exception.getMessage().contains("Ontology model is null or empty"),
                "Exception message should indicate the model issue");
    }

    @Test
    void exportToTurtle_WithEmptyModel_ThrowsTurtleExportException() {
        // Arrange
        OntModel emptyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        TurtleExporter emptyExporter = new TurtleExporter(emptyModel, resourceMap, modelName, modelProperties, effectiveNamespace);

        // Act & Assert
        TurtleExportException exception = assertThrows(TurtleExportException.class,
                emptyExporter::exportToTurtle,
                "Should throw TurtleExportException for empty model");

        assertTrue(exception.getMessage().contains("Ontology model is null or empty"),
                "Exception message should indicate the empty model issue");
    }

    @Test
    void isEmptyLiteralStatement_WithVariousInputs_DetectsCorrectly() throws Exception {
        // Test the isEmptyLiteralStatement helper method
        Method method = TurtleExporter.class.getDeclaredMethod("isEmptyLiteralStatement", Statement.class);
        method.setAccessible(true);

        // Create test statements
        Resource subject = ontModel.createResource(effectiveNamespace + "test");
        Property prop = ontModel.createProperty(effectiveNamespace + "testProp");

        // Test with empty literal
        Statement emptyStmt = ontModel.createStatement(subject, prop, ontModel.createLiteral(""));
        Boolean isEmpty = (Boolean) method.invoke(exporter, emptyStmt);
        assertTrue(isEmpty, "Should detect empty literal");

        // Test with whitespace-only literal
        Statement whitespaceStmt = ontModel.createStatement(subject, prop, ontModel.createLiteral("   "));
        Boolean isWhitespaceEmpty = (Boolean) method.invoke(exporter, whitespaceStmt);
        assertTrue(isWhitespaceEmpty, "Should detect whitespace-only literal as empty");

        // Test with non-empty literal
        Statement nonEmptyStmt = ontModel.createStatement(subject, prop, ontModel.createLiteral("value"));
        Boolean isNotEmpty = (Boolean) method.invoke(exporter, nonEmptyStmt);
        assertFalse(isNotEmpty, "Should not detect non-empty literal as empty");

        // Test with resource object
        Statement resourceStmt = ontModel.createStatement(subject, prop, ontModel.createResource(effectiveNamespace + "object"));
        Boolean resourceIsEmpty = (Boolean) method.invoke(exporter, resourceStmt);
        assertFalse(resourceIsEmpty, "Should not detect resource statement as empty");
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
                () -> assertTrue(result.contains("PREFIX") || result.contains("@prefix"),
                        "Should contain prefix declarations"),
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

    private void testComplexPropertyMapping(String sourceProperty, String expectedPropertyURI, String value) {
        setupModelWithCustomProperty(sourceProperty, value);
        Model parsedModel = exportAndParseModel();
        Property expectedProperty = parsedModel.createProperty(expectedPropertyURI);
        assertTrue(parsedModel.contains(null, expectedProperty, (RDFNode) null),
                "Should map " + sourceProperty + " to " + expectedPropertyURI);
    }

    private void testBooleanPropertyMapping() {
        setupModelWithCustomProperty(JE_PPDF, "true");
        Model parsedModel = exportAndParseModel();

        String expectedPropertyURI = DEFAULT_NS + AGENDOVY_104 + JE_PPDF_LONG;
        Property expectedProperty = parsedModel.createProperty(expectedPropertyURI);

        assertTrue(parsedModel.contains(null, expectedProperty, (RDFNode) null),
                "Should map boolean property");
        assertTrue(hasBooleanLiteralWithValue(parsedModel, true),
                "Should have properly typed boolean value");
    }

    private void testDomainPropertyMapping() {
        setupModelWithCustomProperty(DEFINICNI_OBOR, effectiveNamespace + "DomainClass");
        Model parsedModel = exportAndParseModel();
        assertTrue(parsedModel.contains(null, RDFS.domain, (RDFNode) null),
                "Should transform custom domain to RDFS domain");
    }

    private void testRangePropertyMapping() {
        setupModelWithCustomProperty(OBOR_HODNOT, effectiveNamespace + "RangeClass");
        Model parsedModel = exportAndParseModel();
        assertTrue(parsedModel.contains(null, RDFS.range, (RDFNode) null),
                "Should transform custom range to RDFS range");
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

    private boolean hasEmptyLiterals(Model model) {
        StmtIterator stmtIter = model.listStatements();
        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.next();
            if (stmt.getObject().isLiteral()) {
                String value = stmt.getObject().asLiteral().getString();
                if (value == null || value.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasConceptSchemeWithCorrectTypes(Model model) {
        StmtIterator schemeIter = model.listStatements(null, RDF.type, SKOS.ConceptScheme);
        while (schemeIter.hasNext()) {
            Resource conceptScheme = schemeIter.next().getSubject();
            String vocabularyTypeURI = "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/slovník";
            Resource vocabularyType = model.createResource(vocabularyTypeURI);
            if (conceptScheme.hasProperty(RDF.type, vocabularyType)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCustomDefinitionProperty(Model model) {
        Property customDefProp = model.createProperty(effectiveNamespace + DEFINICE);
        return model.contains(null, customDefProp, (RDFNode) null);
    }

    private boolean hasCustomDomainProperty(Model model) {
        Property customDomainProp = model.createProperty(effectiveNamespace + DEFINICNI_OBOR);
        return model.contains(null, customDomainProp, (RDFNode) null);
    }

    private boolean hasCustomRangeProperty(Model model) {
        Property customRangeProp = model.createProperty(effectiveNamespace + OBOR_HODNOT);
        return model.contains(null, customRangeProp, (RDFNode) null);
    }

    private boolean hasXSDSchemaStatements(Model model) {
        StmtIterator iter = model.listStatements();
        while (iter.hasNext()) {
            Statement stmt = iter.next();
            Resource subject = stmt.getSubject();
            if (subject.getURI() != null && subject.getURI().startsWith("http://www.w3.org/2001/XMLSchema#")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBaseSchemaStatements(Model model) {
        StmtIterator iter = model.listStatements();
        while (iter.hasNext()) {
            Statement stmt = iter.next();
            Resource subject = stmt.getSubject();
            if (subject.getURI() != null && subject.getURI().startsWith("http://www.w3.org/2001/XMLSchema#")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBaseSchemaConceptsWithSKOSType(Model model) {
        String[] baseSchemaURIs = {
                OFN_NAMESPACE + POJEM,
                OFN_NAMESPACE + VLASTNOST,
                OFN_NAMESPACE + VZTAH,
                OFN_NAMESPACE + TRIDA,
                OFN_NAMESPACE + TSP,
                OFN_NAMESPACE + TOP,
                OFN_NAMESPACE + VEREJNY_UDAJ,
                OFN_NAMESPACE + NEVEREJNY_UDAJ
        };

        for (String uri : baseSchemaURIs) {
            Resource resource = model.createResource(uri);
            if (model.contains(resource, RDF.type, SKOS.Concept)) {
                return true;
            }
        }
        return false;
    }

    // ================= SETUP METHODS =================

    private void setupMinimalOntologyModel() {
        setupMinimalOntologyModelWithNamespace(effectiveNamespace);
    }

    private void setupMinimalOntologyModelWithNamespace(String namespace) {
        Resource ontology = ontModel.createOntology(namespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        Resource ofnPojemType = ontModel.createResource(OFN_NAMESPACE + POJEM);
        Resource testConcept = ontModel.createResource(namespace + "test-concept");
        testConcept.addProperty(RDF.type, ofnPojemType);
        testConcept.addProperty(RDFS.label, "Test Concept", "cs");
        resourceMap.put("test-concept", testConcept);
    }

    private void setupModelWithConcepts() {
        setupMinimalOntologyModel();

        Resource ofnPojemType = ontModel.createResource(OFN_NAMESPACE + POJEM);

        Resource concept2 = ontModel.createResource(effectiveNamespace + "concept-2");
        concept2.addProperty(RDF.type, ofnPojemType);
        concept2.addProperty(RDFS.label, "Second Concept", "cs");
        resourceMap.put("concept-2", concept2);
    }

    private void setupModelWithOFNConcepts() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "ofn-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        Resource ofnPojemType = ontModel.createResource(OFN_NAMESPACE + POJEM);

        Resource concept = ontModel.createResource(effectiveNamespace + "ofn-concept");
        concept.addProperty(RDF.type, ofnPojemType);
        concept.addProperty(RDFS.label, "OFN Concept", "cs");
        resourceMap.put("ofn-concept", concept);
    }

    private void setupModelWithCustomProperty(String propertyName, String value) {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");
        Property customProp = ontModel.createProperty(effectiveNamespace + propertyName);

        if (value.startsWith("http://") || value.contains(effectiveNamespace)) {
            concept.addProperty(customProp, ontModel.createResource(value));
        } else {
            concept.addProperty(customProp, value);
        }
    }

    private void setupModelWithBooleanValue(String booleanValue) {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");
        Property ppdfProp = ontModel.createProperty(effectiveNamespace + JE_PPDF);
        concept.addProperty(ppdfProp, booleanValue);
    }

    private void setupModelWithEmptyLiterals() {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");

        Property emptyProp = ontModel.createProperty(effectiveNamespace + "emptyProperty");
        concept.addProperty(emptyProp, "");
        concept.addProperty(emptyProp, "   ");
        concept.addProperty(emptyProp, "valid-value");
    }

    private void setupModelWithSpecificEmptyValue(String emptyValue) {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");

        Property testProp = ontModel.createProperty(effectiveNamespace + "testProperty");
        if (emptyValue != null) {
            concept.addProperty(testProp, emptyValue);
        } else {
            concept.addProperty(testProp, ontModel.createLiteral(""));
        }
    }

    private void setupModelWithDefinitions() {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");

        Property defProp = ontModel.createProperty(effectiveNamespace + DEFINICE);
        concept.addProperty(defProp, "Czech definition", "cs");
        concept.addProperty(defProp, "English definition", "en");
    }

    private void setupModelWithDomainAndRange() {
        setupMinimalOntologyModel();
        Resource concept = resourceMap.get("test-concept");

        Property domainProp = ontModel.createProperty(effectiveNamespace + DEFINICNI_OBOR);
        Resource domainClass = ontModel.createResource(effectiveNamespace + "DomainClass");
        concept.addProperty(domainProp, domainClass);

        Property rangeProp = ontModel.createProperty(effectiveNamespace + OBOR_HODNOT);
        Resource rangeClass = ontModel.createResource(effectiveNamespace + "RangeClass");
        concept.addProperty(rangeProp, rangeClass);
    }

    private void setupModelWithMultilingualContent() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "multilingual-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        Resource ofnPojemType = ontModel.createResource(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "multilingual-concept");
        concept.addProperty(RDF.type, ofnPojemType);
        concept.addProperty(RDFS.label, "Czech Concept", "cs");
        concept.addProperty(RDFS.label, "English Concept", "en");
        resourceMap.put("multilingual-concept", concept);
    }

    private void setupModelWithDifferentConceptTypes() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "typed-vocab");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        Resource pojemClass = ontModel.createResource(OFN_NAMESPACE + POJEM);
        Resource tridaClass = ontModel.createResource(OFN_NAMESPACE + TRIDA);
        Resource vlastnostClass = ontModel.createResource(OFN_NAMESPACE + VLASTNOST);

        // Class concept
        Resource classConcept = ontModel.createResource(effectiveNamespace + "class-concept");
        classConcept.addProperty(RDF.type, pojemClass);
        classConcept.addProperty(RDF.type, tridaClass);
        classConcept.addProperty(RDFS.label, "Class Concept", "cs");

        // Property concept
        Resource propertyConcept = ontModel.createResource(effectiveNamespace + "property-concept");
        propertyConcept.addProperty(RDF.type, pojemClass);
        propertyConcept.addProperty(RDF.type, vlastnostClass);
        propertyConcept.addProperty(RDFS.label, "Property Concept", "cs");
    }

    private void setupModelWithBaseSchemaResources() {
        setupMinimalOntologyModel();

        // Create OFN concept type
        Resource ofnPojemType = ontModel.createResource(OFN_NAMESPACE + POJEM);

        // Add base schema classes - these should not be converted to SKOS concepts
        Resource pojemResource = ontModel.createResource(OFN_NAMESPACE + POJEM);
        pojemResource.addProperty(RDF.type, ofnPojemType);
        pojemResource.addProperty(RDFS.label, "Pojem", "cs");

        Resource vlastnostResource = ontModel.createResource(OFN_NAMESPACE + VLASTNOST);
        vlastnostResource.addProperty(RDF.type, ofnPojemType);
        vlastnostResource.addProperty(RDFS.label, "Vlastnost", "cs");

        // Add XSD schema resource that should be completely filtered out
        Resource xsdStringResource = ontModel.createResource("http://www.w3.org/2001/XMLSchema#string");
        xsdStringResource.addProperty(RDFS.label, "string");
    }

    private void setupModelWithMultipleOntologies() {
        Resource ontology1 = ontModel.createOntology(effectiveNamespace + "vocab-1");
        ontology1.addProperty(RDF.type, OWL2.Ontology);

        Resource ontology2 = ontModel.createOntology(effectiveNamespace + "vocab-2");
        ontology2.addProperty(RDF.type, OWL2.Ontology);

        Resource pojemClass = ontModel.createResource(OFN_NAMESPACE + POJEM);

        Resource concept1 = ontModel.createResource(effectiveNamespace + "concept-1");
        concept1.addProperty(RDF.type, pojemClass);
        concept1.addProperty(RDFS.label, "Concept 1", "cs");

        Resource concept2 = ontModel.createResource(effectiveNamespace + "concept-2");
        concept2.addProperty(RDF.type, pojemClass);
        concept2.addProperty(RDFS.label, "Concept 2", "cs");

        resourceMap.put("ontology", ontology1); // Use first ontology as primary
    }

    private void setupModelWithoutOntologyIRI() {
        // Create model without proper ontology setup
        Resource pojemClass = ontModel.createResource(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "orphan-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(RDFS.label, "Orphan Concept", "cs");
    }
}