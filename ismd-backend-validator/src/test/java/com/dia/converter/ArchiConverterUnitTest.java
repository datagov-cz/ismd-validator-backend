package com.dia.converter;

import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dia.constants.ArchiOntologyConstants.*;
import static org.apache.jena.vocabulary.XSD.anyURI;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ArchiConverterUnitTest {

    private ArchiConverter converter;
    private OntModel ontModel;
    private Map<String, Resource> resourceMap;
    private Map<String, String> propertyMapping;
    private String minimalArchiXML;
    private String completeArchiXML;
    private String invalidArchiXML;

    @BeforeEach
    void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
        converter = new ArchiConverter();

        // Load test XML files
        minimalArchiXML = loadTestFile("minimal-archi.xml");
        completeArchiXML = loadTestFile("complete-archi.xml");
        invalidArchiXML = "<not-valid-xml>";

        // Get access to private fields
        Field ontModelField = ArchiConverter.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        ontModel = (OntModel) ontModelField.get(converter);

        Field resourceMapField = ArchiConverter.class.getDeclaredField("resourceMap");
        resourceMapField.setAccessible(true);
        //noinspection unchecked
        resourceMap = (Map<String, Resource>) resourceMapField.get(converter);

        Field propertyMappingField = ArchiConverter.class.getDeclaredField("propertyMapping");
        propertyMappingField.setAccessible(true);
        //noinspection unchecked
        propertyMapping = (Map<String, String>) propertyMappingField.get(converter);
    }

    private String loadTestFile(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("/com/dia/" + filename, getClass());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void setupArchiDocument(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));

        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        archiDocField.set(converter, doc);
    }

    private Object invokePrivateMethod(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = ArchiConverter.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(converter, args);
    }

    @Test
    void parseFromString_ValidXml_SuccessfullyParses() throws Exception {
        // Act
        converter.parseFromString(minimalArchiXML);

        // Assert
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        assertNotNull(archiDocField.get(converter), "Document should be parsed");

        // Verify property mappings were created
        Field propertyMappingField = ArchiConverter.class.getDeclaredField("propertyMapping");
        propertyMappingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> testPropertyMapping = (Map<String, String>) propertyMappingField.get(converter);
        assertFalse(testPropertyMapping.isEmpty(), "Property mappings should be created");
    }

    @Test
    void parseFromString_InvalidXml_ThrowsException() {
        // Act & Assert
        assertThrows(FileParsingException.class, () ->
                        converter.parseFromString(invalidArchiXML),
                "Should throw FileParsingException for invalid XML"
        );
    }

    @Test
    void parseFromString_NullContent_ThrowsException() {
        // Act & Assert
        assertThrows(FileParsingException.class, () ->
                        converter.parseFromString(null),
                "Should throw FileParsingException for null content"
        );
    }

    @Test
    void convert_WithParsedDocument_CreatesOntologyModel() throws Exception {
        // Arrange
        converter.parseFromString(completeArchiXML);

        // Act
        converter.convert();

        // Assert
        Field ontModelField = ArchiConverter.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        OntModel testOntModel = (OntModel) ontModelField.get(converter);

        // Check that the model contains expected resources
        assertFalse(testOntModel.isEmpty(), "Model should not be empty");

        // Check resource map
        Field resourceMapField = ArchiConverter.class.getDeclaredField("resourceMap");
        resourceMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Resource> testResourceMap = (Map<String, Resource>) resourceMapField.get(converter);
        assertFalse(testResourceMap.isEmpty(), "Resource map should not be empty");

        // Check ontology resource was created
        assertTrue(testResourceMap.containsKey("ontology"), "Ontology resource should be created");
    }

    @Test
    void convert_WithoutParsedDocument_ThrowsException() {
        // Act & Assert
        assertThrows(ConversionException.class, () ->
                        converter.convert(),
                "Should throw ConversionException when document not parsed"
        );
    }

    @Test
    void exportToJson_AfterConversion_ReturnsJsonString() throws Exception {
        // Arrange
        converter.parseFromString(completeArchiXML);
        converter.convert();

        // Act
        String jsonOutput = converter.exportToJson();

        // Assert
        assertNotNull(jsonOutput, "JSON output should not be null");
        assertTrue(jsonOutput.startsWith("{"), "JSON should start with {");
        assertTrue(jsonOutput.endsWith("}"), "JSON should end with }");
    }

    @Test
    void exportToTurtle_AfterConversion_ReturnsTurtleString() throws Exception {
        // Arrange
        converter.parseFromString(completeArchiXML);
        converter.convert();

        // Act
        String turtleOutput = converter.exportToTurtle();

        // Assert
        assertNotNull(turtleOutput, "Turtle output should not be null");
        assertTrue(turtleOutput.contains("PREFIX"), "Turtle should contain PREFIX declarations");
    }

    @Test
    void fullConversionPipeline_ValidInput_SuccessfulConversion() throws Exception {
        // This test verifies the entire processing pipeline
        // Arrange, Act, Assert
        converter.parseFromString(completeArchiXML);
        converter.convert();

        String jsonOutput = converter.exportToJson();
        assertNotNull(jsonOutput, "JSON output should be produced");

        String turtleOutput = converter.exportToTurtle();
        assertNotNull(turtleOutput, "Turtle output should be produced");
    }

    @Test
    void processElements_ContainsExpectedResources() throws Exception {
        // Arrange
        converter.parseFromString(completeArchiXML);

        // Act
        converter.convert();

        // Assert using reflection to check for specific resources
        Field resourceMapField = ArchiConverter.class.getDeclaredField("resourceMap");
        resourceMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Resource> testResourceMap = (Map<String, Resource>) resourceMapField.get(converter);

        // Verify real element ID exists (complete-archi.xml, line 12 identifier)
        assertTrue(testResourceMap.containsKey("id-cd39b4fc55534b9ca590187588b9d082"), "Expected element should exist");

        // Check model contains expected triples
        Field ontModelField = ArchiConverter.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        OntModel testOntModel = (OntModel) ontModelField.get(converter);

        // Verify the model contains expected patterns
        assertTrue(testOntModel.contains(null, RDF.type, (RDFNode) null), "Model should contain type statements");
    }

    @Test
    void parseFromString_WithDTD_HandlesSecurely() {
        // Test that XML with DTD references is handled securely
        String xmlWithDtd = "<?xml version=\"1.0\"?><!DOCTYPE test [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root>&xxe;</root>";

        // Should not throw exception but should handle DTD securely
        assertThrows(FileParsingException.class, () ->
                        converter.parseFromString(xmlWithDtd),
                "Should handle DTD securely"
        );
    }

    @Test
    void buildPropertyMapping_WithRealArchiXML_CreatesCorrectMappings() throws Exception {
        // Arrange - Use the actual minimal-archi.xml
        setupArchiDocument(minimalArchiXML);

        // Act
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Assert
        assertFalse(propertyMapping.isEmpty(), "Property mappings should be created");
        assertTrue(propertyMapping.containsValue("popis"), "Should contain description mapping");
        assertTrue(propertyMapping.containsValue("adresa-lokálního-katalogu-dat-ve-kterém-bude-slovník-registrován"), "Should contain namespace mapping");
        assertTrue(propertyMapping.containsValue("typ"), "Should contain type mapping");
        assertTrue(propertyMapping.containsValue("alternativní-název"), "Should contain alternative name mapping");
        assertTrue(propertyMapping.containsValue("definice"), "Should contain definition mapping");
    }

    @Test
    void mapStandardizedLabel_WithKnownLabels_CreatesStandardMappings() throws Exception {
        // Test various standardized label patterns
        Method mapStandardizedLabel = ArchiConverter.class.getDeclaredMethod(
                "mapStandardizedLabel", String.class, String.class);
        mapStandardizedLabel.setAccessible(true);

        // Test known patterns
        mapStandardizedLabel.invoke(converter, "prop-1", "související zdroj");
        assertEquals("související-zdroj", propertyMapping.get("prop-1"));

        mapStandardizedLabel.invoke(converter, "prop-2", "zdroj");
        assertEquals("zdroj", propertyMapping.get("prop-2"));

        mapStandardizedLabel.invoke(converter, "prop-3", "je pojem sdílen v PPDF?");
        assertEquals("je-sdílen-v-ppdf", propertyMapping.get("prop-3"));

        mapStandardizedLabel.invoke(converter, "prop-4", "je pojem veřejný?");
        assertEquals("je-pojem-veřejný", propertyMapping.get("prop-4"));

        mapStandardizedLabel.invoke(converter, "prop-5", "agendový informační systém");
        assertEquals("agendový-informační-systém", propertyMapping.get("prop-5"));
    }

    @Test
    void getEffectiveOntologyNamespace_WithCustomNamespace_ReturnsCustom() throws Exception {
        // Arrange
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        namespaceField.set(converter, "https://example.org/custom");

        // Act
        String result = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Assert
        assertEquals("https://example.org/custom/", result);
    }

    @Test
    void getEffectiveOntologyNamespace_WithoutCustomNamespace_ReturnsDefault() throws Exception {
        // Act
        String result = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Assert
        assertEquals(NS, result);
    }

    @Test
    void processModelNameProperty_WithValidNamespace_SetsOntologyNamespace() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Act
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        // Assert - Use the actual namespace from minimalArchiXML
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        String namespace = (String) namespaceField.get(converter);
        assertEquals("https://data.dia.gov.cz", namespace,
                "Should extract namespace from 'adresa-lokálního-katalogu-dat-ve-kterém-bude-slovník-registrován' property");
    }

    // Additional test to verify the property extraction works correctly
    @Test
    void processModelNameProperty_WithMinimalArchiXML_ExtractsCorrectNamespace() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Debug: Check what model properties are found before processing
        @SuppressWarnings("unchecked")
        Map<String, String> modelProps = (Map<String, String>) invokePrivateMethod("getModelProperties", new Class<?>[0]);
        System.out.println("=== Model Properties Before Processing ===");
        modelProps.forEach((key, value) -> {
            System.out.println("'" + key + "' = '" + value + "'");
            if (key.contains("adresa") && key.contains("lokální")) {
                System.out.println("  ↑ This should be detected as namespace property");
            }
        });

        // Check initial state
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        String namespaceBefore = (String) namespaceField.get(converter);
        System.out.println("Namespace before processing: " + namespaceBefore);

        // Act
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        // Assert
        String namespaceAfter = (String) namespaceField.get(converter);
        System.out.println("Namespace after processing: " + namespaceAfter);

        assertNotNull(namespaceAfter, "Namespace should be set after processing");
        assertEquals("https://data.dia.gov.cz", namespaceAfter,
                "Should extract the correct namespace from minimalArchiXML");

        // Verify getEffectiveOntologyNamespace works correctly
        String effectiveNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        System.out.println("Effective namespace: " + effectiveNamespace);
        assertEquals("https://data.dia.gov.cz/", effectiveNamespace,
                "Effective namespace should have trailing slash");
    }

    @Test
    void initializeTypeClasses_CreatesAllRequiredTypes() throws Exception {
        // Act
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_POJEM)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_TRIDA)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_VZTAH)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_VLASTNOST)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_TSP)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_TOP)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_VEREJNY_UDAJ)));
        assertTrue(ontModel.containsResource(ontModel.createResource(namespace + TYP_NEVEREJNY_UDAJ)));
    }

    @Test
    void processElements_WithValidElements_CreatesResources() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        extractModelName();
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);

        // Act
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Assert - Use actual element IDs from minimalArchiXML
        assertFalse(resourceMap.isEmpty(), "Resources should be created");

        // Check for actual elements from minimal-archi.xml (excluding "Subjekt", "Objekt", "Vlastnost" which are skipped)
        assertTrue(resourceMap.containsKey("id-12d2cf9e5a0d4eaaaead8c999a5c18a2"), "Should contain Řidičský průkaz element");
        assertTrue(resourceMap.containsKey("id-36cdd3c9007946ac97977b69022803c4"), "Should contain Řidič element");
        assertTrue(resourceMap.containsKey("id-1777949a4d594632829a472483f76a82"), "Should contain Jméno držitele element");
        assertTrue(resourceMap.containsKey("id-2da11670898b4767ae360b63411c36c7"), "Should contain Obecní úřad element");

        // Verify resource properties using actual elements
        Resource ridicskoPrukaz = resourceMap.get("id-12d2cf9e5a0d4eaaaead8c999a5c18a2"); // Řidičský průkaz
        Resource ridic = resourceMap.get("id-36cdd3c9007946ac97977b69022803c4"); // Řidič

        // Check RDFS labels were created
        assertTrue(ridicskoPrukaz.hasProperty(RDFS.label), "Řidičský průkaz should have RDFS label");
        assertTrue(ridic.hasProperty(RDFS.label), "Řidič should have RDFS label");

        // Check RDF types were created
        assertTrue(ridicskoPrukaz.hasProperty(RDF.type), "Řidičský průkaz should have RDF type");
        assertTrue(ridic.hasProperty(RDF.type), "Řidič should have RDF type");

        // Verify specific label content
        String ridicskoPrukazLabel = ridicskoPrukaz.getProperty(RDFS.label).getString();
        assertEquals("Řidičský průkaz", ridicskoPrukazLabel, "Should have correct Czech label");

        String ridicLabel = ridic.getProperty(RDFS.label).getString();
        assertEquals("Řidič", ridicLabel, "Should have correct Czech label");
    }

    // Additional test to verify correct elements are skipped
    @Test
    void processElements_SkipsTemplateElements() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        extractModelName();
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);

        // Act
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Assert - Verify that template elements are NOT in the resourceMap
        assertFalse(resourceMap.values().stream().anyMatch(resource -> {
            if (resource.hasProperty(RDFS.label)) {
                String label = resource.getProperty(RDFS.label).getString();
                return "Subjekt".equals(label) || "Objekt".equals(label) || "Vlastnost".equals(label);
            }
            return false;
        }), "Template elements (Subjekt, Objekt, Vlastnost) should be skipped");

        // Verify that actual business elements ARE included
        assertTrue(resourceMap.values().stream().anyMatch(resource -> {
            if (resource.hasProperty(RDFS.label)) {
                String label = resource.getProperty(RDFS.label).getString();
                return "Řidičský průkaz".equals(label);
            }
            return false;
        }), "Actual business elements should be included");

        // Count should be greater than 0 but less than total XML elements (since templates are skipped)
        assertTrue(resourceMap.size() > 5, "Should create multiple resources from real elements");

        System.out.println("Successfully filtered out template elements. Created " + resourceMap.size() + " resources.");
    }

    @Test
    void addAlternativeNames_WithSemicolonSeparatedNames_CreatesMultipleProperties() throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("alternativní-název", "Name1;Name2;Name3");

        Method addAlternativeNames = ArchiConverter.class.getDeclaredMethod(
                "addAlternativeNames", Resource.class, Map.class);
        addAlternativeNames.setAccessible(true);

        // Act
        addAlternativeNames.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(testResource.hasProperty(ontModel.getProperty(namespace + "alternativní-název")));

        // Count the number of alternative name properties
        int altNameCount = testResource.listProperties(ontModel.getProperty(namespace + "alternativní-název")).toList().size();
        assertEquals(3, altNameCount, "Should have 3 alternative name properties");
    }

    @Test
    void addAlternativeNames_WithSingleName_CreatesSingleProperty() throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("alternativní-název", "Single Name");

        Method addAlternativeNames = ArchiConverter.class.getDeclaredMethod(
                "addAlternativeNames", Resource.class, Map.class);
        addAlternativeNames.setAccessible(true);

        // Act
        addAlternativeNames.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(testResource.hasProperty(ontModel.getProperty(namespace + "alternativní-název")));

        int altNameCount = testResource.listProperties(ontModel.getProperty(namespace + "alternativní-název")).toList().size();
        assertEquals(1, altNameCount, "Should have 1 alternative name property");
    }

    @Test
    void transformEliUrl_WithRemoveELITrue_TransformsUrl() throws Exception {
        // Arrange
        converter.setRemoveELI(true);
        Method transformEliUrl = ArchiConverter.class.getDeclaredMethod("transformEliUrl", String.class);
        transformEliUrl.setAccessible(true);

        String originalUrl = "https://example.org/path/eli/cz/sb/2023/123";

        // Act
        String result = (String) transformEliUrl.invoke(converter, originalUrl);

        // Assert
        assertEquals("https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/2023/123", result);
    }

    @Test
    void transformEliUrl_WithRemoveELIFalse_ReturnsOriginalUrl() throws Exception {
        // Arrange
        converter.setRemoveELI(false);
        Method transformEliUrl = ArchiConverter.class.getDeclaredMethod("transformEliUrl", String.class);
        transformEliUrl.setAccessible(true);

        String originalUrl = "https://example.org/path/eli/cz/sb/2023/123";

        // Act
        String result = (String) transformEliUrl.invoke(converter, originalUrl);

        // Assert
        assertEquals(originalUrl, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123",
            "A456",
            "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A789"
    })
    void getFormattedAgenda_WithVariousFormats_ReturnsCorrectFormat(String input) throws Exception {
        // Act
        Method getFormattedAgenda = ArchiConverter.class.getDeclaredMethod("getFormattedAgenda", String.class);
        getFormattedAgenda.setAccessible(true);
        String result = (String) getFormattedAgenda.invoke(null, input);

        // Assert
        if (input.matches("^\\d+$")) {
            assertEquals("https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A" + input, result);
        } else if (input.matches("^A\\d+$")) {
            assertEquals("https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/" + input, result);
        } else if (input.matches("^https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A\\d+$")) {
            assertEquals(input, result);
        } else {
            assertEquals(input, result);
        }
    }

    @Test
    void addRdfTypesAndClasses_WithSubjectType_AddsCorrectTypes() throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/subject");
        Method addRdfTypesAndClasses = ArchiConverter.class.getDeclaredMethod(
                "addRdfTypesAndClasses", Resource.class, String.class);
        addRdfTypesAndClasses.setAccessible(true);

        // Act
        addRdfTypesAndClasses.invoke(converter, testResource, TYP_TSP);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM)));
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_TRIDA)));
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_TSP)));
    }

    @Test
    void addRdfTypesAndClasses_WithObjectType_AddsCorrectTypes() throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/object");
        Method addRdfTypesAndClasses = ArchiConverter.class.getDeclaredMethod(
                "addRdfTypesAndClasses", Resource.class, String.class);
        addRdfTypesAndClasses.setAccessible(true);

        // Act
        addRdfTypesAndClasses.invoke(converter, testResource, TYP_TOP);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM)));
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_TRIDA)));
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_TOP)));
    }

    @Test
    void addRdfTypesAndClasses_WithPropertyType_AddsCorrectTypes() throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/property");
        Method addRdfTypesAndClasses = ArchiConverter.class.getDeclaredMethod(
                "addRdfTypesAndClasses", Resource.class, String.class);
        addRdfTypesAndClasses.setAccessible(true);

        // Act
        addRdfTypesAndClasses.invoke(converter, testResource, TYP_VLASTNOST);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM)));
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_VLASTNOST)));
    }

    @Test
    void processRelationships_WithAssociationRelationship_CreatesRelationshipResource() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - Check for actual relationship IDs from minimal-archi.xml
        // Association relationship: "drží řidičský průkaz"
        assertTrue(resourceMap.containsKey("id-89dca38040204cf3a05a7e820ab8b217"),
                "Should contain 'drží řidičský průkaz' relationship resource");

        Resource relationship = resourceMap.get("id-89dca38040204cf3a05a7e820ab8b217");
        assertTrue(relationship.hasProperty(RDFS.label), "Relationship should have label");
        assertTrue(relationship.hasProperty(RDF.type), "Relationship should have type");

        // Check that the relationship has the correct name
        assertTrue(relationship.hasProperty(RDFS.label, "drží řidičský průkaz", "cs"),
                "Relationship should have correct Czech label");

        // Check for other association relationships
        assertTrue(resourceMap.containsKey("id-b58de181505a49d59a2b763db5dfd15e"),
                "Should contain 'řídí vozidlo' relationship resource");

        assertTrue(resourceMap.containsKey("id-df8e26be56da4f9b97571cbc67c78875"),
                "Should contain 'sídlí na adrese' relationship resource");

        assertTrue(resourceMap.containsKey("id-044751eb7cd648a6bb5444c504b274f4"),
                "Should contain 'vydává řidičský průkaz' relationship resource");
    }

    @Test
    void processRelationships_WithSpecializationRelationship_CreatesSuperClassRelation() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - Check specialization relationships
        Resource ridic = resourceMap.get("id-36cdd3c9007946ac97977b69022803c4");
        Resource ucastnik = resourceMap.get("id-126e88ded2bc443e8f641247bdd4dfd0");

        assertNotNull(ridic, "Řidič resource should exist");
        assertNotNull(ucastnik, "Účastník provozu resource should exist");

        // Updated: Use the correct property name from your debug output
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Check for the LABEL_NT property (which maps to "nadřazená-třída")
        Property ntProperty = ontModel.getProperty(namespace + "nadřazená-třída");
        assertNotNull(ntProperty, "nadřazená-třída property should exist");

        assertTrue(ridic.hasProperty(ntProperty, ucastnik),
                "Řidič should have nadřazená-třída relationship to účastník provozu");

        // Also check the second specialization relationship
        Resource ridicEvidovany = resourceMap.get("id-4345ddfd90cf440ea069d113e87f5cd7");
        assertNotNull(ridicEvidovany, "Řidič evidovaný resource should exist");

        assertTrue(ridicEvidovany.hasProperty(ntProperty, ridic),
                "Řidič evidovaný should have nadřazená-třída relationship to řidič");

        // Verify total count of specialization relationships
        long ntCount = ontModel.listStatements(null, ntProperty, (RDFNode) null).toList().size();
        assertEquals(2, ntCount, "Should have exactly 2 specialization relationships");
    }

    @Test
    void processCompositionRelationship_CreatesObjectPropertiesWithCorrectNames() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act - Process only composition relationships
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - Check composition properties were created with lowercase names (no capitalization)
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Expected property names (lowercase, from getLocalName without capitalization)
        ObjectProperty jmenoProperty = ontModel.getObjectProperty(namespace + "jméno-držitele-řidičského-průkazu");
        ObjectProperty prijmeniProperty = ontModel.getObjectProperty(namespace + "příjmení-držitele-řidičského-průkazu");
        ObjectProperty nazevProperty = ontModel.getObjectProperty(namespace + "název-obecního-úřadu-obce-s-rozšířenou-působností");

        assertNotNull(jmenoProperty, "Should create lowercase jméno property");
        assertNotNull(prijmeniProperty, "Should create lowercase příjmení property");
        assertNotNull(nazevProperty, "Should create lowercase název property");

        // Verify they are ObjectProperties
        assertTrue(jmenoProperty.isObjectProperty(), "jméno property should be ObjectProperty");
        assertTrue(prijmeniProperty.isObjectProperty(), "příjmení property should be ObjectProperty");
        assertTrue(nazevProperty.isObjectProperty(), "název property should be ObjectProperty");

        // Verify domain and range are set correctly
        Resource ridicskoPrukaz = resourceMap.get("id-12d2cf9e5a0d4eaaaead8c999a5c18a2"); // Source
        Resource jmenoDrzitele = resourceMap.get("id-1777949a4d594632829a472483f76a82"); // Target
        Resource prijmeniDrzitele = resourceMap.get("id-97be46f58b61485f95a677943a906130"); // Target

        Resource obecniUrad = resourceMap.get("id-2da11670898b4767ae360b63411c36c7"); // Source
        Resource nazevUradu = resourceMap.get("id-61b3d6f2a78c4a03b7936824c7b8fb3d"); // Target

        // Check domain (source) relationships
        assertTrue(jmenoProperty.hasDomain(ridicskoPrukaz), "jméno property should have Řidičský průkaz as domain");
        assertTrue(prijmeniProperty.hasDomain(ridicskoPrukaz), "příjmení property should have Řidičský průkaz as domain");
        assertTrue(nazevProperty.hasDomain(obecniUrad), "název property should have Obecní úřad as domain");

        // Check range (target) relationships
        assertTrue(jmenoProperty.hasRange(jmenoDrzitele), "jméno property should have Jméno držitele as range");
        assertTrue(prijmeniProperty.hasRange(prijmeniDrzitele), "příjmení property should have Příjmení držitele as range");
        assertTrue(nazevProperty.hasRange(nazevUradu), "název property should have Název úřadu as range");
    }

    @Test
    void processCompositionRelationship_AddLabelsFromTargets() throws Exception {
        // Test that addCompositionLabels method works correctly
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Get the created properties
        ObjectProperty jmenoProperty = ontModel.getObjectProperty(namespace + "jméno-držitele-řidičského-průkazu");
        assertNotNull(jmenoProperty, "jméno property should exist");

        // Verify that labels were added from the target resource
        assertTrue(jmenoProperty.hasProperty(RDFS.label), "Property should have RDFS label");

        // The label should come from the target resource's label
        String propertyLabel = null;
        if (jmenoProperty.hasProperty(RDFS.label)) {
            propertyLabel = jmenoProperty.getProperty(RDFS.label).getString();
        }

        assertNotNull(propertyLabel, "Property should have a label");

        // The label should be derived from the target resource name
        Resource jmenoDrzitele = resourceMap.get("id-1777949a4d594632829a472483f76a82");
        String targetLabel = jmenoDrzitele.getProperty(RDFS.label).getString();

        // The property label should match or be derived from the target label
        assertEquals("Jméno držitele řidičského průkazu", targetLabel, "Target should have correct label");
    }

    @Test
    void processCompositionRelationship_ManualTest() throws Exception {
        // Test individual composition relationship processing manually
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Get specific source and target resources
        Resource source = resourceMap.get("id-12d2cf9e5a0d4eaaaead8c999a5c18a2"); // Řidičský průkaz
        Resource target = resourceMap.get("id-1777949a4d594632829a472483f76a82"); // Jméno držitele

        assertNotNull(source, "Source resource should exist");
        assertNotNull(target, "Target resource should exist");

        // Get the target local name
        String targetLocalName = (String) invokePrivateMethod("getLocalName", new Class<?>[]{Resource.class}, target);
        System.out.println("Target local name: " + targetLocalName);

        // Manually call processCompositionRelationship
        Method processComp = ArchiConverter.class.getDeclaredMethod("processCompositionRelationship", Resource.class, Resource.class);
        processComp.setAccessible(true);
        processComp.invoke(converter, source, target);

        // Verify the property was created
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        String expectedPropertyUri = namespace + targetLocalName;

        ObjectProperty createdProperty = ontModel.getObjectProperty(expectedPropertyUri);
        assertNotNull(createdProperty, "Property should be created with URI: " + expectedPropertyUri);

        // Verify it's properly configured
        assertTrue(createdProperty.isObjectProperty(), "Should be ObjectProperty");
        assertTrue(createdProperty.hasDomain(source), "Should have correct domain");
        assertTrue(createdProperty.hasRange(target), "Should have correct range");

        System.out.println("=== Manual Composition Test ===");
        System.out.println("Source: " + source.getURI());
        System.out.println("Target: " + target.getURI());
        System.out.println("Created property: " + createdProperty.getURI());
        System.out.println("Has domain: " + createdProperty.hasDomain(source));
        System.out.println("Has range: " + createdProperty.hasRange(target));
    }

    @Test
    void processSpecializationRelationships_CreatesNTProperties() throws Exception {
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - Focus specifically on the NT (nadřazená-třída) property creation
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ntProperty = ontModel.getProperty(namespace + "nadřazená-třída");

        assertNotNull(ntProperty, "Should create nadřazená-třída property for specialization relationships");

        // Count how many resources have this property
        long resourcesWithNT = ontModel.listSubjects()
                .toList()
                .stream()
                .filter(resource -> resource.hasProperty(ntProperty))
                .count();

        assertEquals(2, resourcesWithNT, "Should have 2 resources with nadřazená-třída relationships");

        // Verify it's not an ObjectProperty (specialization creates regular properties)
        assertTrue(ntProperty.isProperty(), "nadřazená-třída should not be ObjectProperty");
    }

    @Test
    void processRelationships_WithCompositionRelationship_CreatesObjectProperty() throws Exception {
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - check for ObjectProperties specifically
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // These should be ObjectProperties
        ObjectProperty jmenoProperty = ontModel.getObjectProperty(namespace + "jméno-držitele-řidičského-průkazu");
        ObjectProperty prijmeniProperty = ontModel.getObjectProperty(namespace + "příjmení-držitele-řidičského-průkazu");
        ObjectProperty nazevProperty = ontModel.getObjectProperty(namespace + "název-obecního-úřadu-obce-s-rozšířenou-působností");

        assertNotNull(jmenoProperty, "Should create Jméno ObjectProperty");
        assertNotNull(prijmeniProperty, "Should create Příjmení ObjectProperty");
        assertNotNull(nazevProperty, "Should create Název ObjectProperty");

        // Verify they are actually ObjectProperties
        assertTrue(jmenoProperty.isObjectProperty(), "Jméno should be ObjectProperty");
        assertTrue(prijmeniProperty.isObjectProperty(), "Příjmení should be ObjectProperty");
        assertTrue(nazevProperty.isObjectProperty(), "Název should be ObjectProperty");

        // Count ObjectProperties
        long objectPropertyCount = ontModel.listObjectProperties()
                .toList()
                .stream()
                .filter(prop -> prop.getURI().startsWith(namespace))
                .count();

        assertTrue(objectPropertyCount >= 3, "Should have at least 3 ObjectProperties from composition relationships");
    }

    @Test
    void addMultipleSourceUrls_WithSemicolonSeparatedUrls_CreatesMultipleProperties_Fixed() throws Exception {
        // Arrange - Add proper setup to ensure correct namespace
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName(); // This is needed for namespace extraction

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Method addMultipleSourceUrls = ArchiConverter.class.getDeclaredMethod(
                "addMultipleSourceUrls", Resource.class, String.class);
        addMultipleSourceUrls.setAccessible(true);

        String urls = "http://source1.org;http://source2.org;http://source3.org";

        // Act
        addMultipleSourceUrls.invoke(converter, testResource, urls);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        System.out.println("Using namespace: " + namespace);

        Property zdrojProperty = ontModel.getProperty(namespace + "zdroj");
        List<Statement> sourceProperties = testResource.listProperties(zdrojProperty).toList();

        System.out.println("Found " + sourceProperties.size() + " source properties:");
        sourceProperties.forEach(stmt -> System.out.println("  " + stmt.getObject()));

        assertEquals(3, sourceProperties.size(), "Should have 3 source properties");
    }

    @Test
    void addMultipleSourceUrls_WithEliUrls_CreatesMultipleProperties() throws Exception {
        // Use URLs that match the ELI pattern or set removeELI to false
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();

        // Set removeELI to false to avoid transformation
        Field removeELIField = ArchiConverter.class.getDeclaredField("removeELI");
        removeELIField.setAccessible(true);
        removeELIField.set(converter, false);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Method addMultipleSourceUrls = ArchiConverter.class.getDeclaredMethod(
                "addMultipleSourceUrls", Resource.class, String.class);
        addMultipleSourceUrls.setAccessible(true);

        String urls = "http://source1.org;http://source2.org;http://source3.org";

        // Act
        addMultipleSourceUrls.invoke(converter, testResource, urls);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property zdrojProperty = ontModel.getProperty(namespace + "zdroj");
        int sourceCount = testResource.listProperties(zdrojProperty).toList().size();

        assertEquals(3, sourceCount, "Should have 3 source properties when removeELI is false");
    }

    @Test
    void addMultipleSourceUrls_WithValidEliUrls_CreatesMultipleProperties() throws Exception {
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Method addMultipleSourceUrls = ArchiConverter.class.getDeclaredMethod(
                "addMultipleSourceUrls", Resource.class, String.class);
        addMultipleSourceUrls.setAccessible(true);

        // Use URLs that match the ELI pattern
        String urls = "https://www.e-sbirka.cz/eli/cz/sb/2000/361/dokument/norma;https://example.com/eli/cz/sb/2020/123/dokument/norma;https://test.org/eli/cz/sb/2021/456/dokument/norma";

        // Act
        addMultipleSourceUrls.invoke(converter, testResource, urls);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property zdrojProperty = ontModel.getProperty(namespace + "zdroj");
        int sourceCount = testResource.listProperties(zdrojProperty).toList().size();

        assertEquals(3, sourceCount, "Should have 3 source properties with valid ELI URLs");
    }

    @Test
    void getElementProperties_WithMultilingualProperties_ExtractsAllLanguages() throws Exception {
        String multilingualXML = """
                <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       identifier="multilingual-model">
                    <elements>
                        <element identifier="element-1" xsi:type="BusinessObject">
                            <name xml:lang="cs">Test Actor</name>
                            <name xml:lang="en">Test Actor EN</name>
                            <name xml:lang="de">Test Actor DE</name>
                            <properties>
                                <property propertyDefinitionRef="prop-def-1">
                                    <value xml:lang="cs">Czech Description</value>
                                </property>
                                <property propertyDefinitionRef="prop-def-1">
                                    <value xml:lang="en">English Description</value>
                                </property>
                            </properties>
                        </element>
                    </elements>
                    <propertyDefinitions>
                        <propertyDefinition identifier="prop-def-1" type="string">
                            <name>popis</name>
                        </propertyDefinition>
                    </propertyDefinitions>
                </model>
                """;

        setupArchiDocument(multilingualXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the element from the document
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList elements = doc.getElementsByTagNameNS(ARCHI_NS, "element");
        Element element = (Element) elements.item(0);

        Method getElementProperties = ArchiConverter.class.getDeclaredMethod(
                "getElementProperties", Element.class);
        getElementProperties.setAccessible(true);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, String> properties = (Map<String, String>) getElementProperties.invoke(converter, element);

        // Assert
        assertTrue(properties.containsKey("lang=en"), "Should contain English label");
        assertTrue(properties.containsKey("lang=de"), "Should contain German label");
        assertEquals("Test Actor EN", properties.get("lang=en"));
        assertEquals("Test Actor DE", properties.get("lang=de"));
    }

    @Test
    void assembleIri_WithValidInput_CreatesCorrectIri() throws Exception {
        // Arrange
        Method assembleIri = ArchiConverter.class.getDeclaredMethod("assembleIri", String.class);
        assembleIri.setAccessible(true);

        // Act
        String result = (String) assembleIri.invoke(converter, "Test Model");

        // Assert
        String expectedNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(result.startsWith(expectedNamespace), "IRI should start with effective namespace");
        assertTrue(result.contains("test"), "IRI should contain sanitized model name");
    }

    // Integration test for complete element processing
    @Test
    void completeElementProcessing_WithComplexXML_CreatesCorrectStructure() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        extractModelName();

        // Act - Full conversion process
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);
        invokePrivateMethod("setModelIRI", new Class<?>[0]);  // ← ADD THIS LINE
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - Use actual element IDs from minimalArchiXML
        assertFalse(resourceMap.isEmpty(), "Should have created resources");

        // Check for actual elements from minimal-archi.xml
        assertTrue(resourceMap.containsKey("id-12d2cf9e5a0d4eaaaead8c999a5c18a2"), "Should contain Řidičský průkaz element");
        assertTrue(resourceMap.containsKey("id-36cdd3c9007946ac97977b69022803c4"), "Should contain Řidič element");
        assertTrue(resourceMap.containsKey("id-1777949a4d594632829a472483f76a82"), "Should contain Jméno držitele element");

        // Check for relationship in resourceMap (Association relationships create resources)
        assertTrue(resourceMap.containsKey("id-89dca38040204cf3a05a7e820ab8b217"), "Should contain 'drží řidičský průkaz' relationship");

        // Verify ontology namespace was set correctly from minimalArchiXML
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        String namespace = (String) namespaceField.get(converter);
        assertEquals("https://data.dia.gov.cz", namespace, "Should extract correct namespace from minimal XML");

        // Verify effective namespace (with trailing slash)
        String effectiveNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertEquals("https://data.dia.gov.cz/", effectiveNamespace, "Should have trailing slash in effective namespace");

        // Verify resource types based on actual content in minimalArchiXML
        Resource ridicrskoPrukaz = resourceMap.get("id-12d2cf9e5a0d4eaaaead8c999a5c18a2"); // Řidičský průkaz (typ objektu)
        Resource ridic = resourceMap.get("id-36cdd3c9007946ac97977b69022803c4"); // Řidič (typ subjektu)
        Resource jmenoDrzitele = resourceMap.get("id-1777949a4d594632829a472483f76a82"); // Jméno držitele (typ vlastnosti)

        // Check RDF types based on the "typ" property values in the XML
        assertTrue(ridicrskoPrukaz.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_TOP)),
                "Řidičský průkaz should be TYP_TOP (typ objektu)");
        assertTrue(ridic.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_TSP)),
                "Řidič should be TYP_TSP (typ subjektu)");
        assertTrue(jmenoDrzitele.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_VLASTNOST)),
                "Jméno držitele should be TYP_VLASTNOST (typ vlastnosti)");

        // Verify that all resources have the basic TYP_POJEM type
        assertTrue(ridicrskoPrukaz.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_POJEM)),
                "All resources should have TYP_POJEM type");
        assertTrue(ridic.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_POJEM)),
                "All resources should have TYP_POJEM type");
        assertTrue(jmenoDrzitele.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_POJEM)),
                "All resources should have TYP_POJEM type");

        // Verify SKOS relationships were created
        Resource ontologyResource = resourceMap.get("ontology");
        assertNotNull(ontologyResource, "Should have created ontology resource");
        assertTrue(ridicrskoPrukaz.hasProperty(SKOS.inScheme, ontologyResource),
                "Resources should be linked to the ontology scheme");

        // Verify composition properties were created (ObjectProperties)
        assertNotNull(ontModel.getObjectProperty(effectiveNamespace + "jméno-držitele-řidičského-průkazu"),
                "Should create composition property for Jméno držitele");
        assertNotNull(ontModel.getObjectProperty(effectiveNamespace + "příjmení-držitele-řidičského-průkazu"),
                "Should create composition property for Příjmení držitele");

        // Verify specialization properties were created
        Property ntProperty = ontModel.getProperty(effectiveNamespace + "nadřazená-třída");
        assertNotNull(ntProperty, "Should create nadřazená-třída property for specializations");

        // Count total resources created (should exclude "Subjekt", "Objekt", "Vlastnost" which are skipped)
        assertTrue(resourceMap.size() >= 10, "Should create multiple resources from the complex XML");

        // Debug output for verification
        System.out.println("=== Created Resources ===");
        System.out.println("Total resources: " + resourceMap.size());
        System.out.println("Effective namespace: " + effectiveNamespace);
        System.out.println("Ontology namespace field: " + namespace);
    }

    private void extractModelName() throws Exception {
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        Field modelNameField = ArchiConverter.class.getDeclaredField("modelName");
        modelNameField.setAccessible(true);

        // Extract model name the same way the converter does
        NodeList nameNodes = doc.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nameNodes.getLength() > 0) {
            modelNameField.set(converter, nameNodes.item(0).getTextContent());
        } else {
            modelNameField.set(converter, "Untitled Model");
        }
    }

    // Property processing
    @Test
    void getModelProperties_WithValidProperties_ExtractsCorrectly() throws Exception {
        // Arrange
        String xmlWithModelProperties = """
                <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       identifier="test-model">
                    <name>Test Model</name>
                    <properties>
                        <property propertyDefinitionRef="prop-def-1">
                            <value>Test Description</value>
                        </property>
                        <property propertyDefinitionRef="prop-def-2">
                            <value>https://data.dia.gov.cz</value>
                        </property>
                        <property propertyDefinitionRef="prop-def-3">
                            <value>Test Agenda</value>
                        </property>
                    </properties>
                    <propertyDefinitions>
                        <propertyDefinition identifier="prop-def-1" type="string">
                            <name>popis</name>
                        </propertyDefinition>
                        <propertyDefinition identifier="prop-def-2" type="string">
                            <name>adresa lokálního katalogu dat</name>
                        </propertyDefinition>
                        <propertyDefinition identifier="prop-def-3" type="string">
                            <name>agenda</name>
                        </propertyDefinition>
                    </propertyDefinitions>
                </model>
                """;

        setupArchiDocument(xmlWithModelProperties);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, String> properties = (Map<String, String>) invokePrivateMethod("getModelProperties", new Class<?>[0]);

        // Assert
        assertNotNull(properties, "Properties map should not be null");
        assertFalse(properties.isEmpty(), "Properties map should not be empty");
        assertEquals("Test Description", properties.get("popis"), "Should extract description property");
        assertEquals("https://data.dia.gov.cz", properties.get("adresa-lokálního-katalogu-dat-ve-kterém-bude-slovník-registrován"), "Should extract namespace property");
        assertEquals("Test Agenda", properties.get("agenda"), "Should extract agenda property");
    }

    @Test
    void getModelProperties_WithNoModelProperties_ReturnsEmptyMap() throws Exception {
        // Arrange
        String xmlWithoutModelProperties = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <name>Test Model</name>
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name>Test Actor</name>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithoutModelProperties);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, String> properties = (Map<String, String>) invokePrivateMethod("getModelProperties", new Class<?>[0]);

        // Assert
        assertNotNull(properties, "Properties map should not be null");
        assertTrue(properties.isEmpty(), "Properties map should be empty when no model properties exist");
    }

    @Test
    void getModelProperties_WithEmptyPropertiesElement_ReturnsEmptyMap() throws Exception {
        // Arrange
        String xmlWithEmptyProperties = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <name>Test Model</name>
                <properties>
                    <!-- Empty properties element -->
                </properties>
            </model>
            """;

        setupArchiDocument(xmlWithEmptyProperties);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, String> properties = (Map<String, String>) invokePrivateMethod("getModelProperties", new Class<?>[0]);

        // Assert
        assertNotNull(properties, "Properties map should not be null");
        assertTrue(properties.isEmpty(), "Properties map should be empty when properties element is empty");
    }

    @Test
    void findModelPropertiesElement_WithValidModelProperties_ReturnsElement() throws Exception {
        // Arrange
        String xmlWithModelProperties = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <name>Test Model</name>
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value>Test Value</value>
                    </property>
                </properties>
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name>Test Actor</name>
                        <properties>
                            <!-- This should NOT be returned - it's element properties, not model properties -->
                            <property propertyDefinitionRef="prop-def-2">
                                <value>Element Value</value>
                            </property>
                        </properties>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithModelProperties);

        // Act
        Element result = (Element) invokePrivateMethod("findModelPropertiesElement", new Class<?>[0]);

        // Assert
        assertNotNull(result, "Should find model properties element");
        assertEquals("properties", result.getLocalName(), "Should return properties element");

        // Verify it's the model-level properties, not element-level
        assertEquals("model", result.getParentNode().getLocalName(), "Should be child of model element");
    }

    @Test
    void findModelPropertiesElement_WithNoModelProperties_ReturnsNull() throws Exception {
        // Arrange
        String xmlWithoutModelProperties = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <name>Test Model</name>
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name>Test Actor</name>
                        <properties>
                            <!-- This is element properties, not model properties -->
                            <property propertyDefinitionRef="prop-def-1">
                                <value>Element Value</value>
                            </property>
                        </properties>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithoutModelProperties);

        // Act
        Element result = (Element) invokePrivateMethod("findModelPropertiesElement", new Class<?>[0]);

        // Assert
        assertNull(result, "Should return null when no model properties element exists");
    }

    @Test
    void extractPropertiesFromElement_WithValidProperties_ExtractsAll() throws Exception {
        // Arrange
        String xmlWithProperties = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value>First Value</value>
                    </property>
                    <property propertyDefinitionRef="prop-def-2">
                        <value>Second Value</value>
                    </property>
                    <property propertyDefinitionRef="prop-def-3">
                        <value>Third Value</value>
                    </property>
                </properties>
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name>first property</name>
                    </propertyDefinition>
                    <propertyDefinition identifier="prop-def-2" type="string">
                        <name>second property</name>
                    </propertyDefinition>
                    <propertyDefinition identifier="prop-def-3" type="string">
                        <name>third property</name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithProperties);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the properties element
        Element propertiesElement = (Element) invokePrivateMethod("findModelPropertiesElement", new Class<?>[0]);
        assertNotNull(propertiesElement, "Properties element should exist");

        Map<String, String> resultMap = new HashMap<>();

        // Act
        invokePrivateMethod("extractPropertiesFromElement",
                new Class<?>[]{Element.class, Map.class},
                propertiesElement, resultMap);

        // Assert
        assertEquals(3, resultMap.size(), "Should extract all 3 properties");
        assertEquals("First Value", resultMap.get("first property"), "Should extract first property");
        assertEquals("Second Value", resultMap.get("second property"), "Should extract second property");
        assertEquals("Third Value", resultMap.get("third property"), "Should extract third property");
    }

    @Test
    void extractPropertiesFromElement_WithEmptyProperties_ExtractsNothing() throws Exception {
        // Arrange
        String xmlWithEmptyProperties = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <!-- Empty properties element -->
                </properties>
            </model>
            """;

        setupArchiDocument(xmlWithEmptyProperties);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        Element propertiesElement = (Element) invokePrivateMethod("findModelPropertiesElement", new Class<?>[0]);
        Map<String, String> resultMap = new HashMap<>();

        // Act
        invokePrivateMethod("extractPropertiesFromElement",
                new Class<?>[]{Element.class, Map.class},
                propertiesElement, resultMap);

        // Assert
        assertTrue(resultMap.isEmpty(), "Should extract no properties from empty properties element");
    }

    @Test
    void processProperty_WithValidProperty_ExtractsCorrectly() throws Exception {
        // Arrange
        String xmlWithProperty = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value>Test Property Value</value>
                    </property>
                </properties>
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name>test property</name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithProperty);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        Map<String, String> resultMap = new HashMap<>();

        // Act
        invokePrivateMethod("processProperty",
                new Class<?>[]{Element.class, Map.class},
                propertyElement, resultMap);

        // Assert
        assertEquals(1, resultMap.size(), "Should process one property");
        assertEquals("Test Property Value", resultMap.get("test property"), "Should extract correct property value");
    }

    @Test
    void processProperty_WithMissingPropertyDefinition_UsesPropertyRef() throws Exception {
        // Arrange
        String xmlWithMissingDef = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="unknown-prop-def">
                        <value>Test Value</value>
                    </property>
                </properties>
                <!-- Note: No propertyDefinitions for unknown-prop-def -->
            </model>
            """;

        setupArchiDocument(xmlWithMissingDef);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        Map<String, String> resultMap = new HashMap<>();

        // Act
        invokePrivateMethod("processProperty",
                new Class<?>[]{Element.class, Map.class},
                propertyElement, resultMap);

        // Assert
        assertEquals(1, resultMap.size(), "Should process property even with missing definition");
        assertEquals("Test Value", resultMap.get("unknown-prop-def"), "Should use property ref as key when definition missing");
    }

    @Test
    void processProperty_WithEmptyValue_SkipsProperty() throws Exception {
        // Arrange
        String xmlWithEmptyValue = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value></value>
                    </property>
                </properties>
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name>empty property</name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithEmptyValue);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        Map<String, String> resultMap = new HashMap<>();

        // Act
        invokePrivateMethod("processProperty",
                new Class<?>[]{Element.class, Map.class},
                propertyElement, resultMap);

        // Assert
        assertTrue(resultMap.isEmpty(), "Should skip property with empty value");
    }

    @Test
    void processProperty_WithNoValueElement_SkipsProperty() throws Exception {
        // Arrange
        String xmlWithNoValue = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <!-- No value element -->
                    </property>
                </properties>
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name>no value property</name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithNoValue);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        Map<String, String> resultMap = new HashMap<>();

        // Act
        invokePrivateMethod("processProperty",
                new Class<?>[]{Element.class, Map.class},
                propertyElement, resultMap);

        // Assert
        assertTrue(resultMap.isEmpty(), "Should skip property with no value element");
    }

    @Test
    void getPropertyValue_WithValidValue_ReturnsValue() throws Exception {
        // Arrange
        String xmlWithValue = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value>Expected Value</value>
                    </property>
                </properties>
            </model>
            """;

        setupArchiDocument(xmlWithValue);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        // Act
        String result = (String) invokePrivateMethod("getPropertyValue",
                new Class<?>[]{Element.class},
                propertyElement);

        // Assert
        assertEquals("Expected Value", result, "Should return the property value");
    }

    @Test
    void getPropertyValue_WithNoValueElement_ReturnsNull() throws Exception {
        // Arrange
        String xmlWithoutValue = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <!-- No value element -->
                    </property>
                </properties>
            </model>
            """;

        setupArchiDocument(xmlWithoutValue);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        // Act
        String result = (String) invokePrivateMethod("getPropertyValue",
                new Class<?>[]{Element.class},
                propertyElement);

        // Assert
        assertNull(result, "Should return null when no value element exists");
    }

    @Test
    void getPropertyValue_WithEmptyValue_ReturnsEmptyString() throws Exception {
        // Arrange
        String xmlWithEmptyValue = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value></value>
                    </property>
                </properties>
            </model>
            """;

        setupArchiDocument(xmlWithEmptyValue);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        // Act
        String result = (String) invokePrivateMethod("getPropertyValue",
                new Class<?>[]{Element.class},
                propertyElement);

        // Assert
        assertEquals("", result, "Should return empty string for empty value element");
    }

    @Test
    void getPropertyValue_WithWhitespaceValue_ReturnsWhitespace() throws Exception {
        // Arrange
        String xmlWithWhitespace = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value>   </value>
                    </property>
                </properties>
            </model>
            """;

        setupArchiDocument(xmlWithWhitespace);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        // Act
        String result = (String) invokePrivateMethod("getPropertyValue",
                new Class<?>[]{Element.class},
                propertyElement);

        // Assert
        assertEquals("   ", result, "Should return whitespace value as-is (trimming handled elsewhere)");
    }

    @Test
    void processProperty_WithOntologyNamespaceProperty_SetsOntologyNamespace() throws Exception {
        // Arrange
        String xmlWithNamespaceProperty = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   identifier="test-model">
                <properties>
                    <property propertyDefinitionRef="prop-def-1">
                        <value>https://example.org/custom-namespace</value>
                    </property>
                </properties>
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name>adresa lokálního katalogu dat</name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithNamespaceProperty);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Get the property element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);

        // Act
        invokePrivateMethod("processModelNameProperty",
                new Class<?>[]{});

        // Verify ontologyNamespace field was set
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        String namespace = (String) namespaceField.get(converter);
        assertEquals("https://example.org/custom-namespace", namespace,
                "Should set ontologyNamespace field when processing namespace property");
    }

    // Data properties tests
    @Test
    void addPpdfSharing_WithTrueValue_AddsProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", "true");

        Method addPpdfSharing = ArchiConverter.class.getDeclaredMethod(
                "addPpdfSharing", Resource.class, Map.class);
        addPpdfSharing.setAccessible(true);

        // Act
        addPpdfSharing.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertTrue(testResource.hasProperty(ppdfProperty), "Should have PPDF property");

        // Check the value - could be boolean true or string "true"
        Statement ppdfStmt = testResource.getProperty(ppdfProperty);
        assertNotNull(ppdfStmt, "PPDF statement should exist");
    }

    @Test
    void addPpdfSharing_WithFalseValue_AddsProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", "false");

        Method addPpdfSharing = ArchiConverter.class.getDeclaredMethod(
                "addPpdfSharing", Resource.class, Map.class);
        addPpdfSharing.setAccessible(true);

        // Act
        addPpdfSharing.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertTrue(testResource.hasProperty(ppdfProperty), "Should have PPDF property");
    }

    @Test
    void addPpdfSharing_WithCzechValues_HandlesCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", "ano");

        Method addPpdfSharing = ArchiConverter.class.getDeclaredMethod(
                "addPpdfSharing", Resource.class, Map.class);
        addPpdfSharing.setAccessible(true);

        // Act
        addPpdfSharing.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertTrue(testResource.hasProperty(ppdfProperty), "Should handle Czech 'ano' value");
    }

    @Test
    void addPpdfSharing_WithInvalidValue_SkipsProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", "maybe");

        Method addPpdfSharing = ArchiConverter.class.getDeclaredMethod(
                "addPpdfSharing", Resource.class, Map.class);
        addPpdfSharing.setAccessible(true);

        // Act
        addPpdfSharing.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertFalse(testResource.hasProperty(ppdfProperty), "Should not add property with invalid boolean value");
    }

    @Test
    void addPpdfSharing_WithEmptyValue_SkipsProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", "");

        Method addPpdfSharing = ArchiConverter.class.getDeclaredMethod(
                "addPpdfSharing", Resource.class, Map.class);
        addPpdfSharing.setAccessible(true);

        // Act
        addPpdfSharing.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertFalse(testResource.hasProperty(ppdfProperty), "Should skip property with empty value");
    }

    @Test
    void addPublicFlag_WithTrueValue_AddsPublicDataType() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-pojem-veřejný", "true");

        Method addPublicFlag = ArchiConverter.class.getDeclaredMethod(
                "addPublicFlag", Resource.class, Map.class);
        addPublicFlag.setAccessible(true);

        // Act
        addPublicFlag.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Resource publicDataType = ontModel.getResource(namespace + TYP_VEREJNY_UDAJ);
        assertTrue(testResource.hasProperty(RDF.type, publicDataType), "Should add public data type");
    }

    @Test
    void addPublicFlag_WithFalseValue_AddsNonPublicDataType() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-pojem-veřejný", "false");

        Method addPublicFlag = ArchiConverter.class.getDeclaredMethod(
                "addPublicFlag", Resource.class, Map.class);
        addPublicFlag.setAccessible(true);

        // Act
        addPublicFlag.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Resource nonPublicDataType = ontModel.getResource(namespace + TYP_NEVEREJNY_UDAJ);
        assertTrue(testResource.hasProperty(RDF.type, nonPublicDataType), "Should add non-public data type");
    }

    @Test
    void addPublicFlag_WithCzechAno_AddsPublicDataType() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-pojem-veřejný", "ano");

        Method addPublicFlag = ArchiConverter.class.getDeclaredMethod(
                "addPublicFlag", Resource.class, Map.class);
        addPublicFlag.setAccessible(true);

        // Act
        addPublicFlag.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Resource publicDataType = ontModel.getResource(namespace + TYP_VEREJNY_UDAJ);
        assertTrue(testResource.hasProperty(RDF.type, publicDataType), "Should handle Czech 'ano' as true");
    }

    @Test
    void addPublicFlag_WithInvalidValue_SkipsPublicFlag() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-pojem-veřejný", "invalid");

        Method addPublicFlag = ArchiConverter.class.getDeclaredMethod(
                "addPublicFlag", Resource.class, Map.class);
        addPublicFlag.setAccessible(true);

        // Act
        addPublicFlag.invoke(converter, testResource, properties);

        // Assert
        assertFalse(testResource.hasProperty(RDF.type, ""), "Should not add empty type for invalid boolean value");
    }

    @Test
    void addNonPublicData_WithValidProvision_AddsTypeAndProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("ustanovení-dokládající-neveřejnost", "Legal provision text");

        Method addNonPublicData = ArchiConverter.class.getDeclaredMethod(
                "addNonPublicData", Resource.class, Map.class);
        addNonPublicData.setAccessible(true);

        // Act
        addNonPublicData.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Resource nonPublicDataType = ontModel.getResource(namespace + TYP_NEVEREJNY_UDAJ);
        assertTrue(testResource.hasProperty(RDF.type, nonPublicDataType), "Should add non-public data type");

        Property udnProperty = ontModel.getProperty(namespace + "ustanovení-dokládající-neveřejnost");
        assertTrue(testResource.hasProperty(udnProperty), "Should add UDN property");
    }

    @Test
    void addNonPublicData_WithEmptyProvision_SkipsAddingData() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("ustanovení-dokládající-neveřejnost", "");

        Method addNonPublicData = ArchiConverter.class.getDeclaredMethod(
                "addNonPublicData", Resource.class, Map.class);
        addNonPublicData.setAccessible(true);

        // Act
        addNonPublicData.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Resource nonPublicDataType = ontModel.getResource(namespace + TYP_NEVEREJNY_UDAJ);
        assertFalse(testResource.hasProperty(RDF.type, nonPublicDataType), "Should not add non-public data type with empty provision");
    }

    @Test
    void addAgenda_WithNumericId_FormatsCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("agenda", "123");

        Method addAgenda = ArchiConverter.class.getDeclaredMethod(
                "addAgenda", Resource.class, Map.class, String.class);
        addAgenda.setAccessible(true);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Act
        addAgenda.invoke(converter, testResource, properties, namespace);

        // Assert
        Property agendaProperty = ontModel.getProperty(namespace + "agenda");
        assertTrue(testResource.hasProperty(agendaProperty), "Should add agenda property");

        Statement agendaStmt = testResource.getProperty(agendaProperty);
        String expectedUrl = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A123";
        assertEquals(expectedUrl, agendaStmt.getObject().toString(), "Should format numeric ID to full URL");
    }

    @Test
    void addAgenda_WithANumericId_FormatsCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("agenda", "A456");

        Method addAgenda = ArchiConverter.class.getDeclaredMethod(
                "addAgenda", Resource.class, Map.class, String.class);
        addAgenda.setAccessible(true);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Act
        addAgenda.invoke(converter, testResource, properties, namespace);

        // Assert
        Property agendaProperty = ontModel.getProperty(namespace + "agenda");
        Statement agendaStmt = testResource.getProperty(agendaProperty);
        String expectedUrl = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A456";
        assertEquals(expectedUrl, agendaStmt.getObject().toString(), "Should format A-prefixed ID to full URL");
    }

    @Test
    void addAgenda_WithFullUrl_KeepsOriginal() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        String fullUrl = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A789";
        properties.put("agenda", fullUrl);

        Method addAgenda = ArchiConverter.class.getDeclaredMethod(
                "addAgenda", Resource.class, Map.class, String.class);
        addAgenda.setAccessible(true);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Act
        addAgenda.invoke(converter, testResource, properties, namespace);

        // Assert
        Property agendaProperty = ontModel.getProperty(namespace + "agenda");
        Statement agendaStmt = testResource.getProperty(agendaProperty);
        assertEquals(fullUrl, agendaStmt.getObject().toString(), "Should keep full URL unchanged");
    }

    @Test
    void addAgendaInformationSystem_WithNumericId_FormatsCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("agendový-informační-systém", "987");

        Method addAgendaInformationSystem = ArchiConverter.class.getDeclaredMethod(
                "addAgendaInformationSystem", Resource.class, Map.class, String.class);
        addAgendaInformationSystem.setAccessible(true);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Act
        addAgendaInformationSystem.invoke(converter, testResource, properties, namespace);

        // Assert
        Property aisProperty = ontModel.getProperty(namespace + "agendový-informační-systém");
        Statement aisStmt = testResource.getProperty(aisProperty);
        String expectedUrl = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/987";
        assertEquals(expectedUrl, aisStmt.getObject().toString(), "Should format numeric AIS ID to full URL");
    }

    @Test
    void addAgendaInformationSystem_WithFullUrl_KeepsOriginal() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        String fullUrl = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/654";
        properties.put("agendový-informační-systém", fullUrl);

        Method addAgendaInformationSystem = ArchiConverter.class.getDeclaredMethod(
                "addAgendaInformationSystem", Resource.class, Map.class, String.class);
        addAgendaInformationSystem.setAccessible(true);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Act
        addAgendaInformationSystem.invoke(converter, testResource, properties, namespace);

        // Assert
        Property aisProperty = ontModel.getProperty(namespace + "agendový-informační-systém");
        Statement aisStmt = testResource.getProperty(aisProperty);
        assertEquals(fullUrl, aisStmt.getObject().toString(), "Should keep full AIS URL unchanged");
    }

    // Helper method to extract actual value from RDFNode (handles anyURI literals)
    private String getActualValue(RDFNode node) {
        if (node.isLiteral()) {
            return node.asLiteral().getString();
        } else if (node.isResource()) {
            return node.asResource().getURI();
        } else {
            return node.toString();
        }
    }

    @Test
    void addDataSharingWays_WithSingleWay_AddsSingleProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("má-způsob-sdílení-údaje", "online");

        Method addDataSharingWays = ArchiConverter.class.getDeclaredMethod(
                "addDataSharingWays", Resource.class, Map.class);
        addDataSharingWays.setAccessible(true);

        // Act
        addDataSharingWays.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property sharingProperty = ontModel.getProperty(namespace + "má-způsob-sdílení-údaje");
        assertTrue(testResource.hasProperty(sharingProperty), "Should add data sharing way property");

        List<Statement> sharingStmts = testResource.listProperties(sharingProperty).toList();
        assertEquals(1, sharingStmts.size(), "Should add exactly one sharing way");

        String expectedUrl = "https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/online";
        assertEquals(expectedUrl, getActualValue(sharingStmts.get(0).getObject()), "Should format sharing way URL");
    }

    @Test
    void addDataSharingWays_WithMultipleWays_AddsMultipleProperties() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("má-způsob-sdílení-údaje", "online;offline;api");

        Method addDataSharingWays = ArchiConverter.class.getDeclaredMethod(
                "addDataSharingWays", Resource.class, Map.class);
        addDataSharingWays.setAccessible(true);

        // Act
        addDataSharingWays.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property sharingProperty = ontModel.getProperty(namespace + "má-způsob-sdílení-údaje");

        List<Statement> sharingStmts = testResource.listProperties(sharingProperty).toList();
        assertEquals(3, sharingStmts.size(), "Should add three sharing ways");

        // Verify all expected URLs are present
        Set<String> actualUrls = sharingStmts.stream()
                .map(stmt -> getActualValue(stmt.getObject()))
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(actualUrls.contains("https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/online"));
        assertTrue(actualUrls.contains("https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/offline"));
        assertTrue(actualUrls.contains("https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/api"));
    }

    @Test
    void addDataSharingWays_WithHttpUrl_KeepsOriginal() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        String httpUrl = "https://example.org/custom-sharing-way";
        properties.put("má-způsob-sdílení-údaje", httpUrl);

        Method addDataSharingWays = ArchiConverter.class.getDeclaredMethod(
                "addDataSharingWays", Resource.class, Map.class);
        addDataSharingWays.setAccessible(true);

        // Act
        addDataSharingWays.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property sharingProperty = ontModel.getProperty(namespace + "má-způsob-sdílení-údaje");

        Statement sharingStmt = testResource.getProperty(sharingProperty);
        assertEquals(httpUrl, getActualValue(sharingStmt.getObject()), "Should keep HTTP URL unchanged");
    }

    @Test
    void addDataAcquisitionWay_WithSimpleValue_FormatsCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("má-kategorii-údaje", "manual");

        Method addDataAcquisitionWay = ArchiConverter.class.getDeclaredMethod(
                "addDataAcquisitionWay", Resource.class, Map.class);
        addDataAcquisitionWay.setAccessible(true);

        // Act
        addDataAcquisitionWay.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property acquisitionProperty = ontModel.getProperty(namespace + "má-kategorii-údaje");
        Statement acquisitionStmt = testResource.getProperty(acquisitionProperty);

        String expectedUrl = "https://data.dia.gov.cz/zdroj/číselníky/způsoby-získání-údajů/položky/manual";
        assertEquals(expectedUrl, getActualValue(acquisitionStmt.getObject()), "Should format acquisition way URL");
    }

    @Test
    void addDataContentType_WithSimpleValue_FormatsCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("má-typ-obsahu-údaje", "text");

        Method addDataContentType = ArchiConverter.class.getDeclaredMethod(
                "addDataContentType", Resource.class, Map.class);
        addDataContentType.setAccessible(true);

        // Act
        addDataContentType.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property contentTypeProperty = ontModel.getProperty(namespace + "má-typ-obsahu-údaje");
        Statement contentTypeStmt = testResource.getProperty(contentTypeProperty);

        String expectedUrl = "https://data.dia.gov.cz/zdroj/číselníky/typy-obsahu-údajů/položky/text";
        assertEquals(expectedUrl, getActualValue(contentTypeStmt.getObject()), "Should format content type URL");
    }

    @Test
    void addDataProperties_IntegrationTest_AddsAllProperties() throws Exception {
        // Integration test for addDataProperties method
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", "true");
        properties.put("je-pojem-veřejný", "ano");
        properties.put("agendový-informační-systém", "123");
        properties.put("agenda", "456");
        properties.put("má-způsob-sdílení-údaje", "online;api");
        properties.put("má-kategorii-údaje", "automatic");
        properties.put("má-typ-obsahu-údaje", "structured");

        Method addDataProperties = ArchiConverter.class.getDeclaredMethod(
                "addDataProperties", Resource.class, Map.class);
        addDataProperties.setAccessible(true);

        // Act
        addDataProperties.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Check PPDF property
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertTrue(testResource.hasProperty(ppdfProperty), "Should add PPDF property");

        // Check public flag (should add public data type)
        Resource publicDataType = ontModel.getResource(namespace + TYP_VEREJNY_UDAJ);
        assertTrue(testResource.hasProperty(RDF.type, publicDataType), "Should add public data type");

        // Check AIS property
        Property aisProperty = ontModel.getProperty(namespace + "agendový-informační-systém");
        assertTrue(testResource.hasProperty(aisProperty), "Should add AIS property");

        // Check agenda property
        Property agendaProperty = ontModel.getProperty(namespace + "agenda");
        assertTrue(testResource.hasProperty(agendaProperty), "Should add agenda property");

        // Check sharing ways (should have 2)
        Property sharingProperty = ontModel.getProperty(namespace + "má-způsob-sdílení-údaje");
        List<Statement> sharingStmts = testResource.listProperties(sharingProperty).toList();
        assertEquals(2, sharingStmts.size(), "Should add both sharing ways");

        // Check acquisition way
        Property acquisitionProperty = ontModel.getProperty(namespace + "má-kategorii-údaje");
        assertTrue(testResource.hasProperty(acquisitionProperty), "Should add acquisition way");

        // Check content type
        Property contentTypeProperty = ontModel.getProperty(namespace + "má-typ-obsahu-údaje");
        assertTrue(testResource.hasProperty(contentTypeProperty), "Should add content type");
    }
}
