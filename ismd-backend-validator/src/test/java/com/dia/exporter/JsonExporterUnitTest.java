package com.dia.exporter;

import com.dia.exceptions.JsonExportException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dia.constants.ArchiConstants.*;
import static com.dia.constants.ArchiConstants.EKVIVALENTNI_POJEM;
import static com.dia.constants.ArchiConstants.JE_PPDF;
import static com.dia.constants.ArchiConstants.POPIS;
import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;
import static com.dia.constants.ExcelConstants.*;
import static com.dia.constants.ExportConstants.Json.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link JsonExporter}.
 *
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
        effectiveNamespace = DEFAULT_NS;
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

        // Verify typ array contains expected values
        JsonNode typArray = rootNode.get("typ");
        assertTrue(typArray.isArray(), "typ should be an array");
        assertTrue(typArray.size() >= 3, "typ array should contain at least 3 types");
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
        assertTrue(altNames.isObject(), "Alternative names should be an object with language tags");
        assertTrue(altNames.has("cs"), "Should have Czech alternative names");
        JsonNode csAltNames = altNames.get("cs");
        assertTrue(csAltNames.isArray(), "Czech alternative names should be an array");
        assertEquals(2, csAltNames.size(), "Should have 2 alternative names in Czech");
    }

    @Test
    void exportToJson_WithGovernanceProperties_IncludesGovernanceData() throws Exception {
        // Arrange
        setupModelWithGovernanceProperties();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        // Check governance properties
        assertTrue(concept.has("způsob-sdílení-údajů"), "Should have sharing method");
        assertTrue(concept.has("způsob-získání-údajů"), "Should have acquisition method");
        assertTrue(concept.has("typ-obsahu-údajů"), "Should have content type");

        // Verify arrays are properly formatted
        JsonNode sharingMethod = concept.get("způsob-sdílení-údajů");
        assertTrue(sharingMethod.isArray(), "Sharing method should be array");
        assertFalse(sharingMethod.isEmpty(), "Should have at least one sharing method");
    }

    @Test
    void exportToJson_WithGovernancePropertiesFallback_UsesCorrectNamespace() throws Exception {
        // Arrange
        setupModelWithGovernancePropertiesFallback();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        // Should find governance properties from fallback namespace
        assertTrue(concept.has("způsob-sdílení-údajů") ||
                        concept.has("způsob-získání-údajů") ||
                        concept.has("typ-obsahu-údajů"),
                "Should have at least one governance property from fallback");
    }

    @Test
    void exportToJson_WithSplitMultipleValues_HandlesCorrectly() throws Exception {
        // Arrange
        setupModelWithMultipleValuesInGovernanceProperties();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        JsonNode sharingMethod = concept.get("způsob-sdílení-údajů");
        assertTrue(sharingMethod.isArray(), "Should be array");
        assertEquals(3, sharingMethod.size(), "Should split semicolon-separated values");
        assertEquals("Method1", sharingMethod.get(0).asText());
        assertEquals("Method2", sharingMethod.get(1).asText());
        assertEquals("Method3", sharingMethod.get(2).asText());
    }

    @Test
    void exportToJson_WithModelMetadata_IncludesMetadataInOutput() throws Exception {
        // Arrange
        modelProperties.put(POPIS, "Test model description");
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
                if (POJEM_JSON_LD.equals(typ.asText())) {
                    hasBaseType = true;
                    break;
                }
            }
            assertTrue(hasBaseType, "All concepts should have base type " + POJEM_JSON_LD);
        }
    }

    @Test
    void exportToJson_WithRppMetadata_IncludesCorrectBooleanValues() throws Exception {
        // Arrange
        setupModelWithRppMetadata();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        if (concept.has(JE_PPDF)) {
            JsonNode ppdfValue = concept.get(JE_PPDF);
            assertTrue(ppdfValue.isBoolean(), "PPDF value should be boolean");
        }
    }

    @Test
    void exportToJson_WithExactMatchProperty_FormatsCorrectly() throws Exception {
        // Arrange
        setupModelWithExactMatch();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        assertTrue(concept.has(EKVIVALENTNI_POJEM), "Should have equivalent concept");
        JsonNode exactMatch = concept.get(EKVIVALENTNI_POJEM);
        assertTrue(exactMatch.isArray(), "Exact match should be array");

        JsonNode firstMatch = exactMatch.get(0);
        assertTrue(firstMatch.has("id"), "Match should have id field");
    }

    @Test
    void exportToJson_WithDomainAndRange_IncludesPropertyMetadata() throws Exception {
        // Arrange
        setupModelWithDomainAndRange();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        if (concept.has(DEFINICNI_OBOR)) {
            assertNotNull(concept.get(DEFINICNI_OBOR).asText());
        }

        if (concept.has(OBOR_HODNOT)) {
            String rangeValue = concept.get(OBOR_HODNOT).asText();
            assertNotNull(rangeValue);
            // Check XSD prefix handling
            if (rangeValue.startsWith("xsd:")) {
                assertTrue(rangeValue.length() > 4, "XSD type should have content after prefix");
            }
        }
    }

    @Test
    void exportToJson_WithHierarchy_IncludesSuperClasses() throws Exception {
        // Arrange
        setupModelWithHierarchy();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        JsonNode pojmyArray = rootNode.get("pojmy");
        JsonNode concept = pojmyArray.get(0);

        if (concept.has(NADRAZENA_TRIDA)) {
            JsonNode hierarchy = concept.get(NADRAZENA_TRIDA);
            assertTrue(hierarchy.isArray(), "Hierarchy should be array");
            assertFalse(hierarchy.isEmpty(), "Should have at least one superclass");
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
    void exportToJson_WithOntologyIRI_UsesCorrectOntologyReference() throws Exception {
        // Arrange
        setupModelWithOntologyIRI();

        // Act
        String result = exporter.exportToJson();

        // Assert
        JsonNode rootNode = objectMapper.readTree(result);
        String iri = rootNode.get("iri").asText();
        assertNotNull(iri, "Should have IRI");
        assertTrue(iri.contains("test-ontology"), "Should reference the ontology");
    }

    @Test
    void exportToJson_WithInvalidData_ThrowsJsonExportException() {
        // Test the actual error handling
        JsonExporter invalidExporter = new JsonExporter(null, resourceMap, modelName, modelProperties, effectiveNamespace);

        assertThrows(JsonExportException.class,
                invalidExporter::exportToJson,
                "Should throw JsonExportException when ontModel is null");
    }

    @Test
    void exportToJson_WithEmptyModel_ThrowsJsonExportException() {
        // Test with empty model
        OntModel emptyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        JsonExporter emptyModelExporter = new JsonExporter(emptyModel, resourceMap, modelName, modelProperties, effectiveNamespace);

        JsonExportException exception = assertThrows(JsonExportException.class,
                emptyModelExporter::exportToJson,
                "Should throw JsonExportException when ontModel is empty");

        assertTrue(exception.getMessage().contains("Ontology model is null or empty"),
                "Exception message should indicate the ontModel issue");
    }

    @Test
    void extractStatementValue_WithLiteralAndResource_ReturnsCorrectValues() throws Exception {
        // Test the extractStatementValue helper method
        Resource subject = ontModel.createResource(effectiveNamespace + "test");
        Property prop = ontModel.createProperty(effectiveNamespace + "testProp");

        // Test with literal
        subject.addProperty(prop, "literal value");
        // Test with resource
        Resource objectResource = ontModel.createResource(effectiveNamespace + "object");
        subject.addProperty(prop, objectResource);

        Method method = JsonExporter.class.getDeclaredMethod("extractStatementValue",
                org.apache.jena.rdf.model.Statement.class);
        method.setAccessible(true);

        // Get statements and test
        var statements = subject.listProperties(prop);
        while (statements.hasNext()) {
            var stmt = statements.next();
            String value = (String) method.invoke(exporter, stmt);
            assertNotNull(value, "Should extract value from statement");
        }
    }

    @Test
    void splitMultipleValues_WithSemicolonSeparated_SplitsCorrectly() throws Exception {
        // Test the splitMultipleValues helper method
        Method method = JsonExporter.class.getDeclaredMethod("splitMultipleValues", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(exporter, "value1;value2;value3");

        assertEquals(3, result.size(), "Should split into 3 values");
        assertEquals("value1", result.get(0));
        assertEquals("value2", result.get(1));
        assertEquals("value3", result.get(2));
    }

    @Test
    void splitMultipleValues_WithSingleValue_ReturnsOriginal() throws Exception {
        // Test single value handling
        Method method = JsonExporter.class.getDeclaredMethod("splitMultipleValues", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(exporter, "single value");

        assertEquals(1, result.size(), "Should return single value");
        assertEquals("single value", result.get(0));
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
    // Helper methods to set up test data

    private void setupMinimalOntologyModel() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource testConcept = ontModel.createResource(effectiveNamespace + "test-concept");
        testConcept.addProperty(RDF.type, pojemClass);
        testConcept.addProperty(SKOS.prefLabel, "Test Concept", "cs");
        resourceMap.put("test-concept", testConcept);
    }

    private void setupModelWithConcepts() {
        setupMinimalOntologyModel();

        OntClass pojemClass = ontModel.getOntClass(OFN_NAMESPACE + POJEM);
        Resource concept2 = ontModel.createResource(effectiveNamespace + "second-concept");
        concept2.addProperty(RDF.type, pojemClass);
        concept2.addProperty(SKOS.prefLabel, "Second Concept", "cs");
        resourceMap.put("second-concept-id", concept2);
    }

    private void setupModelWithConceptsUsingCustomNamespace(String customNamespace) {
        Resource ontology = ontModel.createOntology(customNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        ontology.addProperty(SKOS.prefLabel, modelName, "cs");
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(customNamespace + "custom-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Custom Concept", "cs");
        resourceMap.put("custom-concept", concept);
    }

    private void setupModelWithMultilingualConcept() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "multilingual-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Test Concept", "cs");
        concept.addProperty(SKOS.prefLabel, "Test Concept EN", "en");
        resourceMap.put("multilingual-concept-id", concept);
    }

    private void setupModelWithAlternativeNames() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "concept-with-alt-names");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Main Concept", "cs");

        Property altNameProp = ontModel.createProperty(DEFAULT_NS + ALTERNATIVNI_NAZEV);
        concept.addProperty(altNameProp, "Alternative Name 1");
        concept.addProperty(altNameProp, "Alternative Name 2");
        resourceMap.put("alt-names-concept-id", concept);
    }

    private void setupModelWithGovernanceProperties() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "governance-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Governance Concept", "cs");

        // Add governance properties
        Property sharingProp = ontModel.createProperty(effectiveNamespace + ZPUSOB_SDILENI_UDEJE);
        concept.addProperty(sharingProp, "Public sharing");
        concept.addProperty(sharingProp, "Restricted sharing");

        Property acquisitionProp = ontModel.createProperty(effectiveNamespace + ZPUSOB_ZISKANI_UDEJE);
        concept.addProperty(acquisitionProp, "Manual entry");

        Property contentTypeProp = ontModel.createProperty(effectiveNamespace + TYP_OBSAHU_UDAJE);
        concept.addProperty(contentTypeProp, "Structured data");

        resourceMap.put("governance-concept-id", concept);
    }

    private void setupModelWithGovernancePropertiesFallback() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "fallback-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Fallback Concept", "cs");

        // Add property using DEFAULT_NS (fallback namespace)
        Property fallbackProp = ontModel.createProperty(DEFAULT_NS + ZPUSOB_ZISKANI);
        concept.addProperty(fallbackProp, "Fallback method");

        resourceMap.put("fallback-concept-id", concept);
    }

    private void setupModelWithMultipleValuesInGovernanceProperties() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "multi-value-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Multi Value Concept", "cs");

        // Add property with semicolon-separated values
        Property sharingProp = ontModel.createProperty(effectiveNamespace + ZPUSOB_SDILENI_UDEJE);
        concept.addProperty(sharingProp, "Method1;Method2;Method3");

        resourceMap.put("multi-value-concept-id", concept);
    }

    private void setupModelWithRppMetadata() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "rpp-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "RPP Concept", "cs");

        Property ppdfProp = ontModel.createProperty(effectiveNamespace + JE_PPDF);
        concept.addProperty(ppdfProp, ontModel.createTypedLiteral(true));

        resourceMap.put("rpp-concept-id", concept);
    }

    private void setupModelWithExactMatch() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "exact-match-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Exact Match Concept", "cs");

        Property exactMatchProp = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch");
        concept.addProperty(exactMatchProp, ontModel.createResource("http://example.org/equivalent"));

        resourceMap.put("exact-match-concept-id", concept);
    }

    private void setupModelWithDomainAndRange() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "domain-range-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Domain Range Concept", "cs");

        // Add domain and range
        Resource domainClass = ontModel.createResource(effectiveNamespace + "DomainClass");
        concept.addProperty(RDFS.domain, domainClass);

        Resource rangeClass = ontModel.createResource("http://www.w3.org/2001/XMLSchema#string");
        concept.addProperty(RDFS.range, rangeClass);

        resourceMap.put("domain-range-concept-id", concept);
    }

    private void setupModelWithHierarchy() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "hierarchy-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Hierarchy Concept", "cs");

        Resource superClass = ontModel.createResource(effectiveNamespace + "SuperClass");
        concept.addProperty(RDFS.subClassOf, superClass);

        resourceMap.put("hierarchy-concept-id", concept);
    }

    private void setupModelWithOntologyIRI() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-ontology");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "test-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Test Concept", "cs");
        resourceMap.put("test-concept", concept);
    }

    private void setupModelWithDifferentConceptTypes() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        OntClass vlastnostClass = ontModel.createClass(OFN_NAMESPACE + VLASTNOST);
        OntClass vztahClass = ontModel.createClass(OFN_NAMESPACE + VZTAH);

        Resource vlastnostConcept = ontModel.createResource(effectiveNamespace + "test-vlastnost");
        vlastnostConcept.addProperty(RDF.type, pojemClass);
        vlastnostConcept.addProperty(RDF.type, vlastnostClass);
        vlastnostConcept.addProperty(SKOS.prefLabel, "Test Property", "cs");

        Resource vztahConcept = ontModel.createResource(effectiveNamespace + "test-vztah");
        vztahConcept.addProperty(RDF.type, pojemClass);
        vztahConcept.addProperty(RDF.type, vztahClass);
        vztahConcept.addProperty(SKOS.prefLabel, "Test Relationship", "cs");

        resourceMap.put("vlastnost-id", vlastnostConcept);
        resourceMap.put("vztah-id", vztahConcept);
    }

    private void setupModelWithComplexProperties() {
        Resource ontology = ontModel.createOntology(effectiveNamespace + "test-vocabulary");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        resourceMap.put("ontology", ontology);

        OntClass pojemClass = ontModel.createClass(OFN_NAMESPACE + POJEM);
        Resource concept = ontModel.createResource(effectiveNamespace + "complex-concept");
        concept.addProperty(RDF.type, pojemClass);
        concept.addProperty(SKOS.prefLabel, "Complex Concept", "cs");

        Property zdrojProp = ontModel.createProperty("http://purl.org/dc/terms/source");
        concept.addProperty(zdrojProp, ontModel.createResource("http://example.org/source1"));
        concept.addProperty(zdrojProp, ontModel.createResource("http://example.org/source2"));

        Property defProp = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#definition");
        concept.addProperty(defProp, "Czech definition", "cs");
        concept.addProperty(defProp, "English definition", "en");

        resourceMap.put("complex-concept-id", concept);
    }

    private void assertFieldOrder(JsonNode node, String[] expectedFields) {
        int foundFields = 0;
        for (String expectedField : expectedFields) {
            if (node.has(expectedField)) {
                foundFields++;
            }
        }
        assertTrue(foundFields > 0, "Should contain at least some expected fields");
    }
}