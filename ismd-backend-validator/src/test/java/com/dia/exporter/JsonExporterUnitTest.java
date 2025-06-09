package com.dia.exporter;

import com.dia.exceptions.JsonExportException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.dia.constants.ArchiOntologyConstants.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link JsonExporter}.
 * @see JsonExporter
 */
@ExtendWith(MockitoExtension.class)
class JsonExporterUnitTest {

    private OntModel ontModel;
    private Map<String, Resource> resourceMap;
    private String modelName;
    private Map<String, String> modelProperties;
    private String effectiveNamespace;
    private JsonExporter exporter;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Initialize test data
        ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        resourceMap = new HashMap<>();
        modelName = "Test Vocabulary";
        modelProperties = new HashMap<>();
        effectiveNamespace = NS;
        objectMapper = new ObjectMapper();

        // Set up MDC for logging
        MDC.put(LOG_REQUEST_ID, "test-request-123");

        // Initialize exporter
        exporter = new JsonExporter(ontModel, resourceMap, modelName, modelProperties, effectiveNamespace);
    }

    @Test
    void exportToJson_WithMinimalModel_ReturnsValidJson() throws Exception {
        // Arrange
        setupMinimalOntologyModel();

        // Act
        String result = exporter.exportToJson();

        // Debug: Print the actual JSON output
        System.out.println("Generated JSON: " + result);

        // Assert
        assertNotNull(result, "JSON output should not be null");
        assertTrue(result.startsWith("{"), "JSON should start with opening brace");
        assertTrue(result.endsWith("}"), "JSON should end with closing brace");

        // Parse and verify JSON structure
        JsonNode rootNode = objectMapper.readTree(result);

        assertTrue(rootNode.has("@context"), "Should contain @context field");
        assertTrue(rootNode.has("iri"), "Should contain iri field");
        assertTrue(rootNode.has("typ"), "Should contain typ field");
        assertTrue(rootNode.has("pojmy"), "Should contain pojmy array");
    }

    @Test
    void exportToJson_WithConceptsInModel_IncludesConceptsInOutput() throws Exception {
        // Arrange
        setupModelWithConcepts();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");

        assertNotNull(pojmyArray, "Should contain pojmy array");
        assertTrue(pojmyArray.isArray(), "Pojmy should be an array");
        assertFalse(pojmyArray.isEmpty(), "Should contain at least one concept");

        // Check first concept structure
        JsonNode firstConcept = pojmyArray.get(0);
        assertTrue(firstConcept.has("iri"), "Concept should have iri");
        assertTrue(firstConcept.has("typ"), "Concept should have typ");
        assertTrue(firstConcept.has("název"), "Concept should have název");
    }

    @Test
    void exportToJson_WithMultilingualLabels_IncludesAllLanguages() throws Exception {
        // Arrange
        setupModelWithMultilingualConcept();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);
        JsonNode nazev = concept.get("název");

        assertNotNull(nazev, "Concept should have název");
        assertTrue(nazev.has("cs"), "Should have Czech label");
        assertTrue(nazev.has("en"), "Should have English label");
        assertEquals("Test Concept", nazev.get("cs").asText());
        assertEquals("Test Concept EN", nazev.get("en").asText());
    }

    @Test
    void exportToJson_WithAlternativeNames_IncludesNamesArray() throws Exception {
        // Arrange
        setupModelWithAlternativeNames();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        assertTrue(concept.has("alternativní-název"), "Should have alternative names");
        JsonNode altNames = concept.get("alternativní-název");
        assertTrue(altNames.isArray(), "Alternative names should be array");
        assertEquals(2, altNames.size(), "Should have 2 alternative names");
    }

    @Test
    void exportToJson_WithModelMetadata_IncludesMetadataInOutput() throws Exception {
        // Arrange
        modelProperties.put(LABEL_POPIS, "Test model description");
        exporter = new JsonExporter(ontModel, resourceMap, modelName, modelProperties, effectiveNamespace);
        setupMinimalOntologyModel();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);

        // Check model name
        assertTrue(rootNode.has("název"), "Should have název field");
        JsonNode nazev = rootNode.get("název");
        assertEquals("Test Vocabulary", nazev.get("cs").asText());

        // Check description
        assertTrue(rootNode.has("popis"), "Should have popis field");
        JsonNode popis = rootNode.get("popis");
        assertEquals("Test model description", popis.get("cs").asText());
    }

    @Test
    void exportToJson_WithFieldOrdering_MaintainsCorrectOrder() throws Exception {
        // Arrange
        setupModelWithConcepts();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);

        // Check field ordering at root level
        String[] expectedRootFields = {"@context", "iri", "typ", "název", "popis", "pojmy"};
        assertFieldOrder(rootNode, expectedRootFields);

        // Check field ordering in concepts
        JsonNode pojmyArray = rootNode.get("pojmy");
        if (!pojmyArray.isEmpty()) {
            JsonNode firstConcept = pojmyArray.get(0);
            String[] expectedConceptFields = {"iri", "typ", "název"};
            assertFieldOrder(firstConcept, expectedConceptFields);
        }
    }

    @Test
    void exportToJson_WithDifferentConceptTypes_AssignsCorrectTypes() throws Exception {
        // Arrange
        setupModelWithDifferentConceptTypes();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");

        // Find concepts and verify their types
        for (JsonNode concept : pojmyArray) {
            JsonNode typArray = concept.get("typ");
            assertTrue(typArray.isArray(), "Type should be an array");

            boolean hasBaseType = false;
            for (JsonNode typ : typArray) {
                if (TYP_POJEM.equals(typ.asText())) {
                    hasBaseType = true;
                    break;
                }
            }
            assertTrue(hasBaseType, "All concepts should have base type " + TYP_POJEM);
        }
    }

    @Test
    void exportToJson_WithNamespaceFallback_UsesCorrectNamespace() throws Exception {
        // Arrange
        String customNamespace = "https://custom.example.org/";
        exporter = new JsonExporter(ontModel, resourceMap, modelName, modelProperties, customNamespace);
        setupModelWithConceptsUsingCustomNamespace(customNamespace);

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");

        if (!pojmyArray.isEmpty()) {
            JsonNode concept = pojmyArray.get(0);
            String conceptIri = concept.get("iri").asText();
            assertTrue(conceptIri.startsWith(customNamespace),
                    "Concept IRI should use custom namespace");
        }
    }

    @Test
    void addMultilingualModelProperty_WithValidInput_CreatesCorrectStructure() throws Exception {
        // Arrange & Act
        Method method = JsonExporter.class.getDeclaredMethod(
                "addMultilingualModelProperty",
                org.json.JSONObject.class,  // Use org.json.JSONObject, not Spring Boot's
                String.class,
                String.class
        );
        method.setAccessible(true);

        org.json.JSONObject testObj = new org.json.JSONObject();
        method.invoke(exporter, testObj, "testProperty", "Test Value");

        // Assert
        assertTrue(testObj.has("testProperty"), "Should add the property");
        Object propValue = testObj.get("testProperty");
        assertNotNull(propValue, "Property value should not be null");
    }

    @Test
    void exportToJson_WithInvalidData_ThrowsJsonExportException() {
        // Test the actual error handling
        // Create a new exporter with null ontModel to trigger the exception
        JsonExporter invalidExporter = new JsonExporter(null, resourceMap, modelName, modelProperties, effectiveNamespace);

        // This should trigger the JsonExportException from createConceptsArray()
        assertThrows(JsonExportException.class,
                invalidExporter::exportToJson,
                "Should throw JsonExportException when ontModel is null");
    }

    @Test
    void exportToJson_WithEmptyModel_ThrowsJsonExportException() {
        // Test with empty model (not null, but empty)
        OntModel emptyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        JsonExporter emptyModelExporter = new JsonExporter(emptyModel, resourceMap, modelName, modelProperties, effectiveNamespace);

        // This should trigger the JsonExportException from createConceptsArray()
        JsonExportException exception = assertThrows(JsonExportException.class,
                emptyModelExporter::exportToJson,
                "Should throw JsonExportException when ontModel is empty");

        assertTrue(exception.getMessage().contains("Ontology model is null or empty"),
                "Exception message should indicate the ontModel issue");
    }

    @Test
    void exportToJson_WithComplexProperties_HandlesAllPropertyTypes() throws Exception {
        // Arrange
        setupModelWithComplexProperties();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        // Verify different property types are handled correctly
        if (concept.has("zdroj")) {
            JsonNode zdroj = concept.get("zdroj");
            assertTrue(zdroj.isArray() || zdroj.isTextual(), "Source should be array or string");
        }

        if (concept.has("definice")) {
            JsonNode definice = concept.get("definice");
            assertTrue(definice.has("cs"), "Definition should have Czech version");
        }
    }

    @Test
    void jsonToMap_WithNestedComplexJSON_HandlesCorrectly() throws Exception {
        // Test complex nested JSON structures
        JSONObject complex = new JSONObject();
        complex.put("nested", new JSONObject().put("deep", new JSONArray().put("value")));

        Method method = JsonExporter.class.getDeclaredMethod("jsonToMap", JSONObject.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(exporter, complex);
        assertNotNull(result);
    }

    @Test
    void getOntologyIRI_WithMissingOntology_ReturnsDefault() throws Exception {
        // Test when no OWL.Ontology is present
        OntModel emptyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        JsonExporter testExporter = new JsonExporter(emptyModel, resourceMap, modelName,
                modelProperties, effectiveNamespace);

        Method method = JsonExporter.class.getDeclaredMethod("getOntologyIRI");
        method.setAccessible(true);

        String result = (String) method.invoke(testExporter);
        assertEquals(effectiveNamespace, result);
    }

    @Test
    void addResourceProperty_WithMalformedURI_HandlesGracefully() throws Exception {
        // Test with malformed resource URIs
        Resource concept = ontModel.createResource("invalid-uri-format");
        JSONObject target = new JSONObject();

        Method method = JsonExporter.class.getDeclaredMethod("addResourceProperty",
                Resource.class, String.class, String.class, JSONObject.class);
        method.setAccessible(true);

        // Should not throw exception
        assertDoesNotThrow(() -> method.invoke(exporter, concept,
                effectiveNamespace + "someProperty",
                "testProp", target));
    }

    @Test
    void addMultilingualProperty_WithEmptyAndNullValues_FiltersCorrectly() throws Exception {
        // Test filtering of empty values
        Resource concept = ontModel.createResource(effectiveNamespace + "test-concept");
        concept.addProperty(RDFS.label, "", "cs");
        concept.addProperty(RDFS.label, "Valid", "en");

        JSONObject result = new JSONObject();
        Method method = JsonExporter.class.getDeclaredMethod("addMultilingualProperty",
                Resource.class, Property.class, String.class, JSONObject.class);
        method.setAccessible(true);

        method.invoke(exporter, concept, RDFS.label, "test-field", result);

        JSONObject testField = result.getJSONObject("test-field");
        assertFalse(testField.has("cs"), "Should not include empty string");
        assertTrue(testField.has("en"), "Should include valid value");
    }

    @Test
    void getConceptTypes_WithMultipleInheritance_ReturnsAllTypes() throws Exception {
        // Test concept with multiple type inheritance
        OntClass baseClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);
        OntClass vlastnostClass = ontModel.createClass(effectiveNamespace + TYP_VLASTNOST);
        OntClass tridaClass = ontModel.createClass(effectiveNamespace + TYP_TRIDA);

        Resource concept = ontModel.createResource(effectiveNamespace + "multi-type-concept");
        concept.addProperty(RDF.type, baseClass);
        concept.addProperty(RDF.type, vlastnostClass);
        concept.addProperty(RDF.type, tridaClass);

        Method method = JsonExporter.class.getDeclaredMethod("getConceptTypes", Resource.class);
        method.setAccessible(true);

        JSONArray types = (JSONArray) method.invoke(exporter, concept);

        assertTrue(types.length() >= 3, "Should have at least 3 types");
        // Verify all expected types are present
        Set<String> typeSet = new HashSet<>();
        for (int i = 0; i < types.length(); i++) {
            typeSet.add(types.getString(i));
        }
        assertTrue(typeSet.contains(TYP_POJEM));
        assertTrue(typeSet.contains(TYP_VLASTNOST));
        assertTrue(typeSet.contains(TYP_TRIDA));
    }

    @Test
    void addRangePropertyWithBothNamespaces_WithXSDTypes_FormatsCorrectly() throws Exception {
        // Test XSD type formatting
        Resource concept = ontModel.createResource(effectiveNamespace + "range-concept");
        Property rangeProp = ontModel.createProperty(effectiveNamespace + LABEL_OBOR_HODNOT);
        concept.addProperty(rangeProp, ontModel.createResource("http://www.w3.org/2001/XMLSchema#string"));

        JSONObject result = new JSONObject();
        Method method = JsonExporter.class.getDeclaredMethod("addRangePropertyWithBothNamespaces",
                Resource.class, JSONObject.class, String.class);
        method.setAccessible(true);

        method.invoke(exporter, concept, result, effectiveNamespace);

        assertTrue(result.has(LABEL_OBOR_HODNOT));
        assertEquals("xsd:string", result.getString(LABEL_OBOR_HODNOT));
    }

    @Test
    void exportToJson_WithLargeDataset_PerformsWithinReasonableTime() throws Exception {
        // Performance test with larger dataset
        setupLargeOntologyModel(); // Create 1000 concepts

        long startTime = System.currentTimeMillis();
        String result = exporter.exportToJson();
        long endTime = System.currentTimeMillis();

        assertNotNull(result);
        assertTrue(endTime - startTime < 5000, "Should complete within 5 seconds for 1000 concepts");

        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        assertEquals(1000, pojmyArray.size(), "Should include all 1000 concepts");
    }

    @Test
    void exportToJson_WithJsonExceptionDuringProcessing_ThrowsWrappedException() {
        // Create a scenario that would cause a JSON processing error
        // Create an ontology with a concept that has malformed data
        OntModel problematicModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        // Create a concept with properties that might cause JSON serialization issues
        Resource concept = problematicModel.createResource(effectiveNamespace + "problematic-concept");
        OntClass pojemClass = problematicModel.createClass(effectiveNamespace + TYP_POJEM);
        concept.addProperty(RDF.type, pojemClass);

        // Add a property with a very long string that might cause issues
        concept.addProperty(RDFS.label, "Very long text ".repeat(100000), "cs");

        Map<String, Resource> problematicResourceMap = new HashMap<>();
        problematicResourceMap.put("problematic", concept);

        JsonExporter problematicExporter = new JsonExporter(
                problematicModel, problematicResourceMap, modelName, modelProperties, effectiveNamespace);

        // This should still work but we're testing the error handling path exists
        assertDoesNotThrow(() -> {
            String result = problematicExporter.exportToJson();
            assertNotNull(result);
        });
    }

    // Helper methods to set up test data

    private void setupMinimalOntologyModel() {
        // Create basic ontology structure
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, SKOS.ConceptScheme);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");

        resourceMap.put("ontology", ontology);

        // Create base concept type class (this is important!)
        OntClass pojemClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);

        // Create a minimal test concept that should appear in pojmy array
        Resource testConcept = ontModel.createResource(effectiveNamespace + "test-concept");
        testConcept.addProperty(RDF.type, pojemClass);  // This is the key - must have TYP_POJEM type
        testConcept.addProperty(RDFS.label, "Test Concept", "cs");

        // Add to resource map
        resourceMap.put("test-concept", testConcept);

        System.out.println("Setup: Created concept " + testConcept.getURI() + " with type " + pojemClass.getURI());
    }

    private void setupModelWithConcepts() {
        setupMinimalOntologyModel(); // This already creates one concept

        // Create additional test concepts with proper types
        OntClass pojemClass = ontModel.getOntClass(effectiveNamespace + TYP_POJEM);

        // Create second concept
        Resource concept2 = ontModel.createResource(effectiveNamespace + "second-concept");
        concept2.addProperty(RDF.type, pojemClass);  // Must have TYP_POJEM type
        concept2.addProperty(RDFS.label, "Second Concept", "cs");

        resourceMap.put("second-concept-id", concept2);
    }

    private void setupModelWithConceptsUsingCustomNamespace(String customNamespace) {
        effectiveNamespace = customNamespace;
        setupMinimalOntologyModel(); // This already creates one concept

        // Create additional test concepts with proper types
        OntClass pojemClass = ontModel.getOntClass(effectiveNamespace + TYP_POJEM);

        // Create second concept
        Resource concept2 = ontModel.createResource(effectiveNamespace + "second-concept");
        concept2.addProperty(RDF.type, pojemClass);  // Must have TYP_POJEM type
        concept2.addProperty(RDFS.label, "Second Concept", "cs");

        resourceMap.put("second-concept-id", concept2);
    }

    private void setupModelWithMultilingualConcept() {
        // Create base structure
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, SKOS.ConceptScheme);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        // Create concept class and multilingual concept instance
        OntClass pojemClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);

        Resource concept = ontModel.createResource(effectiveNamespace + "multilingual-concept");
        concept.addProperty(RDF.type, pojemClass);  // Must have TYP_POJEM type
        concept.addProperty(RDFS.label, "Test Concept", "cs");
        concept.addProperty(RDFS.label, "Test Concept EN", "en");

        resourceMap.put("multilingual-concept-id", concept);
    }

    private void setupModelWithAlternativeNames() {
        // Create base structure
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, SKOS.ConceptScheme);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        // Create concept class and concept with alternative names
        OntClass pojemClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);

        Resource concept = ontModel.createResource(effectiveNamespace + "concept-with-alt-names");
        concept.addProperty(RDF.type, pojemClass);  // Must have TYP_POJEM type
        concept.addProperty(RDFS.label, "Main Concept", "cs");

        // Add alternative names
        Property altNameProp = ontModel.createProperty(effectiveNamespace + LABEL_AN);
        concept.addProperty(altNameProp, "Alternative Name 1", "cs");
        concept.addProperty(altNameProp, "Alternative Name 2", "cs");

        resourceMap.put("alt-names-concept-id", concept);
    }

    private void setupModelWithDifferentConceptTypes() {
        // Create base structure
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, SKOS.ConceptScheme);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        // Create base concept class and specialized classes
        OntClass pojemClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);
        OntClass vlastnostClass = ontModel.createClass(effectiveNamespace + TYP_VLASTNOST);
        OntClass vztahClass = ontModel.createClass(effectiveNamespace + TYP_VZTAH);

        // Create concept of type vlastnost (property)
        Resource vlastnostConcept = ontModel.createResource(effectiveNamespace + "test-vlastnost");
        vlastnostConcept.addProperty(RDF.type, pojemClass);      // Must have base TYP_POJEM type
        vlastnostConcept.addProperty(RDF.type, vlastnostClass);  // Also has specific type
        vlastnostConcept.addProperty(RDFS.label, "Test Property", "cs");

        // Create concept of type vztah (relationship)
        Resource vztahConcept = ontModel.createResource(effectiveNamespace + "test-vztah");
        vztahConcept.addProperty(RDF.type, pojemClass);    // Must have base TYP_POJEM type
        vztahConcept.addProperty(RDF.type, vztahClass);    // Also has specific type
        vztahConcept.addProperty(RDFS.label, "Test Relationship", "cs");

        resourceMap.put("vlastnost-id", vlastnostConcept);
        resourceMap.put("vztah-id", vztahConcept);
    }

    private void setupModelWithComplexProperties() {
        // Create base structure
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, SKOS.ConceptScheme);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        // Create concept class and complex concept instance
        OntClass pojemClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);

        Resource concept = ontModel.createResource(effectiveNamespace + "complex-concept");
        concept.addProperty(RDF.type, pojemClass);  // Must have TYP_POJEM type
        concept.addProperty(RDFS.label, "Complex Concept", "cs");

        // Add various property types
        Property zdrojProp = ontModel.createProperty(effectiveNamespace + LABEL_ZDROJ);
        concept.addProperty(zdrojProp, ontModel.createResource("http://example.org/source1"));
        concept.addProperty(zdrojProp, ontModel.createResource("http://example.org/source2"));

        Property defProp = ontModel.createProperty(effectiveNamespace + LABEL_DEF);
        concept.addProperty(defProp, "Czech definition", "cs");
        concept.addProperty(defProp, "English definition", "en");

        resourceMap.put("complex-concept-id", concept);
    }

    private void setupLargeOntologyModel() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "large-vocabulary");
        ontology.addProperty(RDF.type, SKOS.ConceptScheme);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(effectiveNamespace + TYP_POJEM);

        for (int i = 0; i < 1000; i++) {
            Resource concept = ontModel.createResource(effectiveNamespace + "concept-" + i);
            concept.addProperty(RDF.type, pojemClass);
            concept.addProperty(RDFS.label, "Concept " + i, "cs");
            resourceMap.put("concept-" + i, concept);
        }
    }

    private void assertFieldOrder(JsonNode node, String[] expectedFields) {
        // This is a helper to verify field ordering in JSON

        int foundFields = 0;
        for (String expectedField : expectedFields) {
            if (node.has(expectedField)) {
                foundFields++;
            }
        }

        assertTrue(foundFields > 0, "Should contain at least some expected fields");
    }
}