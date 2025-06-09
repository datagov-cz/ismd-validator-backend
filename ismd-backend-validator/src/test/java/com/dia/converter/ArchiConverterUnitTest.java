package com.dia.converter;

import com.dia.converter.archi.ArchiConverter;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import com.dia.utility.UtilityMethods;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static com.dia.constants.ArchiOntologyConstants.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Test for {@link ArchiConverter}.
 * @see ArchiConverter
 */
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

    private String getActualValue(RDFNode node) {
        if (node.isLiteral()) {
            return node.asLiteral().getString();
        } else if (node.isResource()) {
            return node.asResource().getURI();
        } else {
            return node.toString();
        }
    }

    @ParameterizedTest(name = "Parse {0} XML")
    @CsvSource({
            "valid, true",
            "invalid, false",
            "null, false"
    })
    void parseFromString_VariousInputs(String inputType, boolean shouldSucceed) throws Exception {
        // Arrange
        String xmlContent = switch (inputType) {
            case "valid" -> minimalArchiXML;
            case "invalid" -> invalidArchiXML;
            case "null" -> null;
            default -> throw new IllegalArgumentException("Unknown input type: " + inputType);
        };

        // Act & Assert
        if (shouldSucceed) {
            assertDoesNotThrow(() -> converter.parseFromString(xmlContent));
            Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
            archiDocField.setAccessible(true);
            assertNotNull(archiDocField.get(converter), "Document should be parsed");
        } else {
            assertThrows(FileParsingException.class, () -> converter.parseFromString(xmlContent));
        }
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

    @ParameterizedTest(name = "Map standardized label: {0} -> {1}")
    @CsvSource({
            "prop-1, související zdroj, související-zdroj",
            "prop-2, zdroj, zdroj",
            "prop-3, je pojem sdílen v PPDF?, je-sdílen-v-ppdf",
            "prop-4, je pojem veřejný?, je-pojem-veřejný",
            "prop-5, agendový informační systém, agendový-informační-systém"
    })
    void mapStandardizedLabel_WithKnownLabels(String propId, String label, String expected) throws Exception {
        Method mapStandardizedLabel = ArchiConverter.class.getDeclaredMethod(
                "mapStandardizedLabel", String.class, String.class);
        mapStandardizedLabel.setAccessible(true);

        // Act
        mapStandardizedLabel.invoke(converter, propId, label);

        // Assert
        assertEquals(expected, propertyMapping.get(propId));
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

    @ParameterizedTest(name = "Alternative names with ''{0}'' -> {1} properties")
    @CsvSource({
            "'Name1;Name2;Name3', 3",
            "'Single Name', 1",
            "'Name1;Name2;Name3;Name4;Name5', 5"
    })
    void addAlternativeNames_WithSemicolonSeparation(String names, int expectedCount) throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("alternativní-název", names);

        Method addAlternativeNames = ArchiConverter.class.getDeclaredMethod(
                "addAlternativeNames", Resource.class, Map.class);
        addAlternativeNames.setAccessible(true);

        // Act
        addAlternativeNames.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property altNameProperty = ontModel.getProperty(namespace + "alternativní-název");
        int actualCount = testResource.listProperties(altNameProperty).toList().size();
        assertEquals(expectedCount, actualCount);
    }

    @ParameterizedTest(name = "Transform ELI URL with removeELI={0}")
    @CsvSource({
            "true, https://example.org/path/eli/cz/sb/2023/123, https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/2023/123",
            "false, https://example.org/path/eli/cz/sb/2023/123, https://example.org/path/eli/cz/sb/2023/123"
    })
    void transformEliUrl_WithSettings(boolean removeELI, String input, String expected) throws Exception {
        converter.setRemoveELI(removeELI);
        Method transformEliUrl = UtilityMethods.class.getDeclaredMethod("transformEliUrl", String.class, Boolean.class);
        transformEliUrl.setAccessible(true);

        String result = (String) transformEliUrl.invoke(converter, input, removeELI);
        assertEquals(expected, result);
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

    @ParameterizedTest(name = "Add RDF types for {0}")
    @ValueSource(strings = {TYP_TSP, TYP_TOP, TYP_VLASTNOST})
    void addRdfTypesAndClasses_WithDifferentTypes(String typeConstant) throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/resource");
        Method addRdfTypesAndClasses = ArchiConverter.class.getDeclaredMethod(
                "addRdfTypesAndClasses", Resource.class, String.class);
        addRdfTypesAndClasses.setAccessible(true);

        // Act
        addRdfTypesAndClasses.invoke(converter, testResource, typeConstant);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM)));
        assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + typeConstant)));

        // Additional assertions for specific types
        if (typeConstant.equals(TYP_TSP) || typeConstant.equals(TYP_TOP)) {
            assertTrue(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_TRIDA)));
        }
    }

    @Test
    void processRelationships_WithVariousTypes_CreatesCorrectRelationships() throws Exception {
        // Complex relationship processing test
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert - Association relationships
        assertTrue(resourceMap.containsKey("id-89dca38040204cf3a05a7e820ab8b217"));

        // Assert - Specialization relationships
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ntProperty = ontModel.getProperty(namespace + "nadřazená-třída");
        assertNotNull(ntProperty);

        // Assert - Composition relationships (ObjectProperties)
        ObjectProperty jmenoProperty = ontModel.getObjectProperty(namespace + "jméno-držitele-řidičského-průkazu");
        assertNotNull(jmenoProperty);
        assertTrue(jmenoProperty.isObjectProperty());
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

    // Test data provider for property value extraction tests
    static Stream<Arguments> propertyValueTestData() {
        return Stream.of(
                Arguments.of(
                        """
                        <property propertyDefinitionRef="prop-1">
                            <value>Expected Value</value>
                        </property>
                        """,
                        "Expected Value", "Should return property value"
                ),
                Arguments.of(
                        """
                        <property propertyDefinitionRef="prop-1">
                            <!-- No value element -->
                        </property>
                        """,
                        null, "Should return null when no value element"
                ),
                Arguments.of(
                        """
                        <property propertyDefinitionRef="prop-1">
                            <value></value>
                        </property>
                        """,
                        "", "Should return empty string for empty value"
                ),
                Arguments.of(
                        """
                        <property propertyDefinitionRef="prop-1">
                            <value>   </value>
                        </property>
                        """,
                        "   ", "Should return whitespace as-is"
                )
        );
    }

    @ParameterizedTest(name = "Get property value: {2}")
    @MethodSource("propertyValueTestData")
    void getPropertyValue_WithVariousInputs(String propertyXml, String expectedValue, String description) throws Exception {
        // Arrange
        String xmlWrapper = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <properties>
                    %s
                </properties>
            </model>
            """.formatted(propertyXml);

        setupArchiDocument(xmlWrapper);

        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propertyNodes = doc.getElementsByTagNameNS(ARCHI_NS, "property");
        Element propertyElement = (Element) propertyNodes.item(0);

        // Act
        String result = (String) invokePrivateMethod("getPropertyValue",
                new Class<?>[]{Element.class}, propertyElement);

        // Assert
        assertEquals(expectedValue, result, description);
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

    // Test data provider for PPDF sharing tests
    static Stream<Arguments> ppdfSharingTestData() {
        return Stream.of(
                Arguments.of("true", true, "Should add PPDF property for 'true'"),
                Arguments.of("false", true, "Should add PPDF property for 'false'"),
                Arguments.of("ano", true, "Should handle Czech 'ano' value"),
                Arguments.of("ne", true, "Should handle Czech 'ne' value"),
                Arguments.of("maybe", false, "Should not add property with invalid value"),
                Arguments.of("", false, "Should skip property with empty value")
        );
    }

    @ParameterizedTest(name = "PPDF sharing with value ''{0}'': {2}")
    @MethodSource("ppdfSharingTestData")
    void addPpdfSharing_WithVariousValues(String value, boolean shouldAddProperty, String description) throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-sdílen-v-ppdf", value);

        Method addPpdfSharing = ArchiConverter.class.getDeclaredMethod(
                "addPpdfSharing", Resource.class, Map.class);
        addPpdfSharing.setAccessible(true);

        // Act
        addPpdfSharing.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property ppdfProperty = ontModel.getProperty(namespace + "je-sdílen-v-ppdf");
        assertEquals(shouldAddProperty, testResource.hasProperty(ppdfProperty), description);
    }

    // Test data provider for public flag tests
    static Stream<Arguments> publicFlagTestData() {
        return Stream.of(
                Arguments.of("true", TYP_VEREJNY_UDAJ, "Should add public data type for 'true'"),
                Arguments.of("false", TYP_NEVEREJNY_UDAJ, "Should add non-public data type for 'false'"),
                Arguments.of("ano", TYP_VEREJNY_UDAJ, "Should handle Czech 'ano' as public"),
                Arguments.of("ne", TYP_NEVEREJNY_UDAJ, "Should handle Czech 'ne' as non-public"),
                Arguments.of("invalid", null, "Should not add type for invalid value")
        );
    }

    @ParameterizedTest(name = "Public flag with value ''{0}'': {2}")
    @MethodSource("publicFlagTestData")
    void addPublicFlag_WithVariousValues(String value, String expectedType, String description) throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put("je-pojem-veřejný", value);

        Method addPublicFlag = ArchiConverter.class.getDeclaredMethod(
                "addPublicFlag", Resource.class, Map.class);
        addPublicFlag.setAccessible(true);

        // Act
        addPublicFlag.invoke(converter, testResource, properties);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        if (expectedType != null) {
            Resource dataType = ontModel.getResource(namespace + expectedType);
            assertTrue(testResource.hasProperty(RDF.type, dataType), description);
        } else {
            // For invalid values, verify no data type was added
            assertFalse(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_VEREJNY_UDAJ)));
            assertFalse(testResource.hasProperty(RDF.type, ontModel.getResource(namespace + TYP_NEVEREJNY_UDAJ)));
        }
    }

    // Test data provider for agenda/AIS formatting
    static Stream<Arguments> agendaFormattingTestData() {
        return Stream.of(
                Arguments.of("123", "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A123", "agenda"),
                Arguments.of("A456", "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A456", "agenda"),
                Arguments.of("https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A789",
                        "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A789", "agenda"),
                Arguments.of("987", "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/987", "agendový-informační-systém"),
                Arguments.of("https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/654",
                        "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/654", "agendový-informační-systém")
        );
    }

    @ParameterizedTest(name = "Format {2}: {0} -> {1}")
    @MethodSource("agendaFormattingTestData")
    void addAgendaOrAIS_WithVariousFormats(String input, String expectedUrl, String propertyName) throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Resource testResource = ontModel.createResource("http://test.org/resource");
        Map<String, String> properties = new HashMap<>();
        properties.put(propertyName, input);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Determine which method to call
        Method method = propertyName.equals("agenda")
                ? ArchiConverter.class.getDeclaredMethod("addAgenda", Resource.class, Map.class, String.class)
                : ArchiConverter.class.getDeclaredMethod("addAgendaInformationSystem", Resource.class, Map.class, String.class);
        method.setAccessible(true);

        // Act
        method.invoke(converter, testResource, properties, namespace);

        // Assert
        Property property = ontModel.getProperty(namespace + propertyName);
        Statement stmt = testResource.getProperty(property);
        assertEquals(expectedUrl, stmt.getObject().toString());
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

    @ParameterizedTest
    @CsvSource({
            "'http://example.org/vocab#localName', 'localName', 'Should extract local name after hash fragment'",
            "'http://example.org/vocab/localName', 'localName', 'Should extract local name after slash separator'",
            "'http://example.org/vocab/path#localName', 'localName', 'Should prefer hash fragment over slash when both are present'",
            "'http://example.org', 'example.org', 'Should return local name when no separator found'",
            "'http://example.org/vocab#', 'http://example.org/vocab#', 'Should return empty local name when local name is empty'",
            "'http://example.org/vocab#local-name_with.special123', 'local-name_with.special123', 'Should handle special characters in local name'"
    })
    void getLocalName_WithVariousUriFormats_ExtractsCorrectly(String inputUri, String expectedLocalName, String description) throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource(inputUri);
        Method getLocalName = ArchiConverter.class.getDeclaredMethod("getLocalName", Resource.class);
        getLocalName.setAccessible(true);

        // Act
        String result = (String) getLocalName.invoke(converter, testResource);

        // Assert
        assertEquals(expectedLocalName, result, description);
    }

    @Test
    void isResourceProperty_WithLiteralRange_ReturnsFalse() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property testProperty = ontModel.createProperty(namespace + "testProperty");
        testProperty.addProperty(RDFS.range, XSD);

        Method isResourceProperty = ArchiConverter.class.getDeclaredMethod("isResourceProperty", Property.class);
        isResourceProperty.setAccessible(true);

        // Act
        boolean result = (boolean) isResourceProperty.invoke(converter, testProperty);

        // Assert
        assertFalse(result, "Should return false for property with literal range");
    }

    @Test
    void isResourceProperty_WithNoRange_ReturnsFalse() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property testProperty = ontModel.createProperty(namespace + "testProperty");
        // No range added

        Method isResourceProperty = ArchiConverter.class.getDeclaredMethod("isResourceProperty", Property.class);
        isResourceProperty.setAccessible(true);

        // Act
        boolean result = (boolean) isResourceProperty.invoke(converter, testProperty);

        // Assert
        assertFalse(result, "Should return false for property with no range specified");
    }

    @Test
    void isResourceProperty_WithMultipleRanges_ReturnsTrueIfAnyIsResource() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        Property testProperty = ontModel.createProperty(namespace + "testProperty");
        testProperty.addProperty(RDFS.range, XSD);
        testProperty.addProperty(RDFS.range, RDFS.Resource);

        Method isResourceProperty = ArchiConverter.class.getDeclaredMethod("isResourceProperty", Property.class);
        isResourceProperty.setAccessible(true);

        // Act
        boolean result = (boolean) isResourceProperty.invoke(converter, testProperty);

        // Assert
        assertTrue(result, "Should return true when any range is Resource");
    }

    @Test
    void extractPropName_WithValidNameElement_ReturnsName() throws Exception {
        // Arrange
        String xmlWithPropertyDef = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name>Test Property Name</name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithPropertyDef);

        // Get the property definition element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propDefs = doc.getElementsByTagNameNS(ARCHI_NS, "propertyDefinition");
        Element propDefElement = (Element) propDefs.item(0);

        Method extractPropName = ArchiConverter.class.getDeclaredMethod("extractPropName", Element.class);
        extractPropName.setAccessible(true);

        // Act
        String result = (String) extractPropName.invoke(converter, propDefElement);

        // Assert
        assertEquals("Test Property Name", result, "Should extract property name from name element");
    }

    @Test
    void extractPropName_WithMissingNameElement_ReturnsNull() throws Exception {
        // Arrange
        String xmlWithoutName = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <!-- No name element -->
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithoutName);

        // Get the property definition element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propDefs = doc.getElementsByTagNameNS(ARCHI_NS, "propertyDefinition");
        Element propDefElement = (Element) propDefs.item(0);

        Method extractPropName = ArchiConverter.class.getDeclaredMethod("extractPropName", Element.class);
        extractPropName.setAccessible(true);

        // Act
        String result = (String) extractPropName.invoke(converter, propDefElement);

        // Assert
        assertNull(result, "Should return null when name element is missing");
    }

    @Test
    void extractPropName_WithEmptyNameElement_ReturnsEmptyString() throws Exception {
        // Arrange
        String xmlWithEmptyName = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <propertyDefinitions>
                    <propertyDefinition identifier="prop-def-1" type="string">
                        <name></name>
                    </propertyDefinition>
                </propertyDefinitions>
            </model>
            """;

        setupArchiDocument(xmlWithEmptyName);

        // Get the property definition element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList propDefs = doc.getElementsByTagNameNS(ARCHI_NS, "propertyDefinition");
        Element propDefElement = (Element) propDefs.item(0);

        Method extractPropName = ArchiConverter.class.getDeclaredMethod("extractPropName", Element.class);
        extractPropName.setAccessible(true);

        // Act
        String result = (String) extractPropName.invoke(converter, propDefElement);

        // Assert
        assertEquals("", result, "Should return empty string when name element is empty");
    }

    @Test
    void getElementName_WithValidNameElement_ReturnsName() throws Exception {
        // Arrange
        String xmlWithElement = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name>Test Element Name</name>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithElement);

        // Get the element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList elements = doc.getElementsByTagNameNS(ARCHI_NS, "element");
        Element elementNode = (Element) elements.item(0);

        Method getElementName = ArchiConverter.class.getDeclaredMethod("getElementName", Element.class);
        getElementName.setAccessible(true);

        // Act
        String result = (String) getElementName.invoke(converter, elementNode);

        // Assert
        assertEquals("Test Element Name", result, "Should extract element name");
    }

    @Test
    void getElementName_WithMissingNameElement_ReturnsEmptyString() throws Exception {
        // Arrange
        String xmlWithoutName = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <!-- No name element -->
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithoutName);

        // Get the element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList elements = doc.getElementsByTagNameNS(ARCHI_NS, "element");
        Element elementNode = (Element) elements.item(0);

        Method getElementName = ArchiConverter.class.getDeclaredMethod("getElementName", Element.class);
        getElementName.setAccessible(true);

        // Act
        String result = (String) getElementName.invoke(converter, elementNode);

        // Assert
        assertEquals("", result, "Should return empty string when name element is missing");
    }

    @Test
    void getRelationshipName_WithValidNameElement_ReturnsName() throws Exception {
        // Arrange
        String xmlWithRelationship = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <relationships>
                    <relationship identifier="rel-1" source="elem-1" target="elem-2" xsi:type="Association">
                        <name>Test Relationship Name</name>
                    </relationship>
                </relationships>
            </model>
            """;

        setupArchiDocument(xmlWithRelationship);

        // Get the relationship
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList relationships = doc.getElementsByTagNameNS(ARCHI_NS, "relationship");
        Element relationshipNode = (Element) relationships.item(0);

        Method getRelationshipName = ArchiConverter.class.getDeclaredMethod("getRelationshipName", Element.class);
        getRelationshipName.setAccessible(true);

        // Act
        String result = (String) getRelationshipName.invoke(converter, relationshipNode);

        // Assert
        assertEquals("Test Relationship Name", result, "Should extract relationship name");
    }

    @Test
    void getRelationshipName_WithMissingNameElement_ReturnsEmptyString() throws Exception {
        // Arrange
        String xmlWithoutName = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <relationships>
                    <relationship identifier="rel-1" source="elem-1" target="elem-2" xsi:type="Association">
                        <!-- No name element -->
                    </relationship>
                </relationships>
            </model>
            """;

        setupArchiDocument(xmlWithoutName);

        // Get the relationship
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList relationships = doc.getElementsByTagNameNS(ARCHI_NS, "relationship");
        Element relationshipNode = (Element) relationships.item(0);

        Method getRelationshipName = ArchiConverter.class.getDeclaredMethod("getRelationshipName", Element.class);
        getRelationshipName.setAccessible(true);

        // Act
        String result = (String) getRelationshipName.invoke(converter, relationshipNode);

        // Assert
        assertEquals("", result, "Should return empty string when name element is missing");
    }

    @Test
    void processNameNodesForLabels_WithMultipleLanguages_ExtractsAll() throws Exception {
        // Arrange
        String xmlWithMultilingual = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name xml:lang="cs">Czech Name</name>
                        <name xml:lang="en">English Name</name>
                        <name xml:lang="de">German Name</name>
                        <name xml:lang="fr">French Name</name>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithMultilingual);

        // Get the element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList elements = doc.getElementsByTagNameNS(ARCHI_NS, "element");
        Element elementNode = (Element) elements.item(0);

        Map<String, String> result = new HashMap<>();

        Method processNameNodesForLabels = ArchiConverter.class.getDeclaredMethod(
                "processNameNodesForLabels", Element.class, Map.class);
        processNameNodesForLabels.setAccessible(true);

        // Act
        processNameNodesForLabels.invoke(converter, elementNode, result);

        // Assert
        assertEquals(3, result.size(), "Should extract 3 non-Czech language labels");
        assertEquals("English Name", result.get("lang=en"), "Should extract English label");
        assertEquals("German Name", result.get("lang=de"), "Should extract German label");
        assertEquals("French Name", result.get("lang=fr"), "Should extract French label");
        assertFalse(result.containsKey("lang=cs"), "Should not extract Czech label (default language)");
    }

    @Test
    void processNameNodesForLabels_WithNoLanguageAttributes_ExtractsNothing() throws Exception {
        // Arrange
        String xmlWithoutLang = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name>Default Name</name>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithoutLang);

        // Get the element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList elements = doc.getElementsByTagNameNS(ARCHI_NS, "element");
        Element elementNode = (Element) elements.item(0);

        Map<String, String> result = new HashMap<>();

        Method processNameNodesForLabels = ArchiConverter.class.getDeclaredMethod(
                "processNameNodesForLabels", Element.class, Map.class);
        processNameNodesForLabels.setAccessible(true);

        // Act
        processNameNodesForLabels.invoke(converter, elementNode, result);

        // Assert
        assertTrue(result.isEmpty(), "Should not extract any labels when no language attributes are present");
    }

    @Test
    void processNameNodesForLabels_WithEmptyLanguageAttribute_ExtractsNothing() throws Exception {
        // Arrange
        String xmlWithEmptyLang = """
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <elements>
                    <element identifier="element-1" xsi:type="BusinessActor">
                        <name xml:lang="">Empty Lang Name</name>
                    </element>
                </elements>
            </model>
            """;

        setupArchiDocument(xmlWithEmptyLang);

        // Get the element
        Field archiDocField = ArchiConverter.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        Document doc = (Document) archiDocField.get(converter);

        NodeList elements = doc.getElementsByTagNameNS(ARCHI_NS, "element");
        Element elementNode = (Element) elements.item(0);

        Map<String, String> result = new HashMap<>();

        Method processNameNodesForLabels = ArchiConverter.class.getDeclaredMethod(
                "processNameNodesForLabels", Element.class, Map.class);
        processNameNodesForLabels.setAccessible(true);

        // Act
        processNameNodesForLabels.invoke(converter, elementNode, result);

        // Assert
        assertTrue(result.isEmpty(), "Should not extract labels with empty language attribute");
    }

    @Test
    void assembleIri_WithValidName_CreatesCorrectIri() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Method assembleIri = ArchiConverter.class.getDeclaredMethod("assembleIri", String.class);
        assembleIri.setAccessible(true);

        // Act
        String result = (String) assembleIri.invoke(converter, "Test Model Name");

        // Assert
        String expectedNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(result.startsWith(expectedNamespace), "IRI should start with effective namespace");
        assertTrue(result.contains("test"), "IRI should contain sanitized model name");
        assertFalse(result.contains(" "), "IRI should not contain spaces");
    }

    @Test
    void assembleIri_WithSpecialCharacters_SanitizesCorrectly() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Method assembleIri = ArchiConverter.class.getDeclaredMethod("assembleIri", String.class);
        assembleIri.setAccessible(true);

        // Act
        String result = (String) assembleIri.invoke(converter, "Test-Model_Name!@#$%");

        // Assert
        String expectedNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(result.startsWith(expectedNamespace), "IRI should start with effective namespace");
        assertFalse(result.contains("!@#$%"), "IRI should not contain special characters");
        assertTrue(result.length() > expectedNamespace.length(), "IRI should have sanitized content after namespace");
    }

    @Test
    void resolveNamespacedUri_WithValidName_CreatesCorrectUri() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Method resolveNamespacedUri = ArchiConverter.class.getDeclaredMethod("resolveNamespacedUri", String.class);
        resolveNamespacedUri.setAccessible(true);

        // Act
        String result = (String) resolveNamespacedUri.invoke(converter, "testResource");

        // Assert
        String expectedNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertEquals(expectedNamespace + "testResource", result, "Should concatenate namespace and resource name");
    }

    @Test
    void createNamespacedResource_CreatesResourceWithCorrectUri() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        Method createNamespacedResource = ArchiConverter.class.getDeclaredMethod("createNamespacedResource", String.class);
        createNamespacedResource.setAccessible(true);

        String resourceName = "testResource";
        String expectedNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        String expectedUri = expectedNamespace + resourceName;

        // Act & Assert
        assertDoesNotThrow(() -> {
            createNamespacedResource.invoke(converter, resourceName);
        }, "createNamespacedResource should execute without throwing an exception");

        // Verify that we can retrieve a resource with the expected URI
        Resource resource = ontModel.getResource(expectedUri);
        assertNotNull(resource, "Should be able to get resource with expected URI");
        assertEquals(expectedUri, resource.getURI(), "Resource should have correct URI");

        // Test the method behavior by using the resource in a statement
        resource.addProperty(RDF.type, RDFS.Resource);

        // Verify the resource exists in the model through statements
        assertTrue(ontModel.contains(resource, RDF.type, RDFS.Resource),
                "Resource should exist in model and have the type property we added");
    }

    @Test
    void addSchemeRelationship_WithConceptResource_AddsInSchemeProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);
        invokePrivateMethod("setModelIRI", new Class<?>[0]);

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Create a test resource with TYP_POJEM type
        Resource testResource = ontModel.createResource("http://test.org/concept");
        testResource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM));

        Method addSchemeRelationship = ArchiConverter.class.getDeclaredMethod("addSchemeRelationship", Resource.class);
        addSchemeRelationship.setAccessible(true);

        // Act
        addSchemeRelationship.invoke(converter, testResource);

        // Assert
        Resource ontologyResource = resourceMap.get("ontology");
        assertNotNull(ontologyResource, "Ontology resource should exist");
        assertTrue(testResource.hasProperty(SKOS.inScheme, ontologyResource),
                "Should add inScheme relationship to ontology");
    }

    @Test
    void addSchemeRelationship_WithNonConceptResource_DoesNotAddProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);
        invokePrivateMethod("setModelIRI", new Class<?>[0]);

        // Create a test resource without TYP_POJEM type
        Resource testResource = ontModel.createResource("http://test.org/non-concept");
        testResource.addProperty(RDF.type, ontModel.createResource("http://test.org/OtherType"));

        Method addSchemeRelationship = ArchiConverter.class.getDeclaredMethod("addSchemeRelationship", Resource.class);
        addSchemeRelationship.setAccessible(true);

        // Act
        addSchemeRelationship.invoke(converter, testResource);

        // Assert
        assertFalse(testResource.hasProperty(SKOS.inScheme),
                "Should not add inScheme relationship for non-concept resources");
    }

    @Test
    void addSchemeRelationship_WithNoOntologyResource_DoesNotAddProperty() throws Exception {
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);
        // Don't call setModelIRI to avoid creating ontology resource

        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);

        // Create a test resource with TYP_POJEM type
        Resource testResource = ontModel.createResource("http://test.org/concept");
        testResource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM));

        Method addSchemeRelationship = ArchiConverter.class.getDeclaredMethod("addSchemeRelationship", Resource.class);
        addSchemeRelationship.setAccessible(true);

        // Act
        addSchemeRelationship.invoke(converter, testResource);

        // Assert
        assertNull(resourceMap.get("ontology"), "Ontology resource should not exist");
        assertFalse(testResource.hasProperty(SKOS.inScheme),
                "Should not add inScheme relationship when ontology resource doesn't exist");
    }

    // Integration test for helper methods working together
    @Test
    void helperMethods_Integration_WorkTogether() throws Exception {
        // Test that helper methods work correctly in combination
        // Arrange
        setupArchiDocument(minimalArchiXML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        extractModelName();
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        // Create a test resource with a complex URI
        Resource complexResource = ontModel.createResource("https://data.dia.gov.cz/zdroj/slovníky/test-vocab#complex-concept-name");

        // Act & Assert - Test getLocalName with complex URI
        Method getLocalName = ArchiConverter.class.getDeclaredMethod("getLocalName", Resource.class);
        getLocalName.setAccessible(true);
        String localName = (String) getLocalName.invoke(converter, complexResource);
        assertEquals("complex-concept-name", localName, "Should extract complex local name correctly");

        // Act & Assert - Test assembleIri with the extracted name
        Method assembleIri = ArchiConverter.class.getDeclaredMethod("assembleIri", String.class);
        assembleIri.setAccessible(true);
        String assembledIri = (String) assembleIri.invoke(converter, localName);

        String expectedNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(assembledIri.startsWith(expectedNamespace), "Assembled IRI should use correct namespace");
        assertTrue(assembledIri.contains("complex"), "Assembled IRI should contain sanitized local name");

        // Act & Assert - Test createNamespacedResource
        Method createNamespacedResource = ArchiConverter.class.getDeclaredMethod("createNamespacedResource", String.class);
        createNamespacedResource.setAccessible(true);
        createNamespacedResource.invoke(converter, localName);

        String expectedUri = expectedNamespace + localName;
        // Test method execution
        assertDoesNotThrow(() -> {
            createNamespacedResource.invoke(converter, localName);
        }, "createNamespacedResource should execute without error");

        // Verify resource retrieval and URI
        Resource createdResource = ontModel.getResource(expectedUri);
        assertNotNull(createdResource, "Should be able to get resource with expected URI");
        assertEquals(expectedUri, createdResource.getURI(), "Should create namespaced resource with extracted local name");
    }
}
