package com.dia.converter;

import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
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
import java.util.Map;

import static com.dia.constants.ArchiOntologyConstants.*;
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

    private static final String COMPLEX_ARCHI_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         name="Complex Model" id="complex-model-id">
            <properties>
                <property propertyDefinitionRef="prop-def-ns">
                    <value>https://example.org/vocabulary/</value>
                </property>
                <property propertyDefinitionRef="prop-def-desc">
                    <value>Test model description</value>
                </property>
            </properties>
            <folder name="Business" id="folder-business" type="business">
                <element xsi:type="archimate:BusinessActor" name="Test Subject" id="element-1">
                    <properties>
                        <property propertyDefinitionRef="prop-def-type">
                            <value>typ subjektu</value>
                        </property>
                        <property propertyDefinitionRef="prop-def-alt-names">
                            <value>Alternative Name 1;Alternative Name 2</value>
                        </property>
                    </properties>
                </element>
                <element xsi:type="archimate:BusinessActor" name="Test Object" id="element-2">
                    <properties>
                        <property propertyDefinitionRef="prop-def-type">
                            <value>typ objektu</value>
                        </property>
                    </properties>
                </element>
            </folder>
            <relationship xsi:type="archimate:AssociationRelationship" 
                         id="rel-1" source="element-1" target="element-2" name="Test Relationship">
                <properties>
                    <property propertyDefinitionRef="prop-def-def">
                        <value>Test relationship definition</value>
                    </property>
                </properties>
            </relationship>
            <propertyDefinition identifier="prop-def-ns" name="adresa lokálního katalogu dat"/>
            <propertyDefinition identifier="prop-def-desc" name="popis"/>
            <propertyDefinition identifier="prop-def-type" name="typ"/>
            <propertyDefinition identifier="prop-def-alt-names" name="alternativní název"/>
            <propertyDefinition identifier="prop-def-def" name="definice"/>
        </archimate:model>
        """;


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
        resourceMap = (Map<String, Resource>) resourceMapField.get(converter);

        Field propertyMappingField = ArchiConverter.class.getDeclaredField("propertyMapping");
        propertyMappingField.setAccessible(true);
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
    void buildPropertyMapping_WithValidXML_CreatesCorrectMappings() throws Exception {
        // Arrange
        setupArchiDocument(COMPLEX_ARCHI_XML);

        // Act
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Assert
        assertFalse(propertyMapping.isEmpty(), "Property mappings should be created");
        assertTrue(propertyMapping.containsValue("popis"), "Should contain description mapping");
        assertTrue(propertyMapping.containsValue("adresa lokálního katalogu dat"), "Should contain namespace mapping");
        assertTrue(propertyMapping.containsValue("typ"), "Should contain type mapping");
        assertTrue(propertyMapping.containsValue("alternativní název"), "Should contain alternative name mapping");
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
        setupArchiDocument(COMPLEX_ARCHI_XML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);

        // Act
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);

        // Assert
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        String namespace = (String) namespaceField.get(converter);
        assertEquals("https://example.org/vocabulary/", namespace);
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
        setupArchiDocument(COMPLEX_ARCHI_XML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);

        // Act
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Assert
        assertFalse(resourceMap.isEmpty(), "Resources should be created");
        assertTrue(resourceMap.containsKey("element-1"), "Should contain first element");
        assertTrue(resourceMap.containsKey("element-2"), "Should contain second element");

        // Verify resource properties
        Resource element1 = resourceMap.get("element-1");
        assertTrue(element1.hasProperty(RDFS.label), "Element should have label");
        assertTrue(element1.hasProperty(RDF.type), "Element should have type");
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
        setupArchiDocument(COMPLEX_ARCHI_XML);
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);

        // Act
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert
        assertTrue(resourceMap.containsKey("rel-1"), "Should contain relationship resource");
        Resource relationship = resourceMap.get("rel-1");
        assertTrue(relationship.hasProperty(RDFS.label), "Relationship should have label");
        assertTrue(relationship.hasProperty(RDF.type), "Relationship should have type");
    }

    @Test
    void addMultipleSourceUrls_WithSemicolonSeparatedUrls_CreatesMultipleProperties() throws Exception {
        // Arrange
        Resource testResource = ontModel.createResource("http://test.org/resource");
        Method addMultipleSourceUrls = ArchiConverter.class.getDeclaredMethod(
                "addMultipleSourceUrls", Resource.class, String.class);
        addMultipleSourceUrls.setAccessible(true);

        String urls = "http://source1.org;http://source2.org;http://source3.org";

        // Act
        addMultipleSourceUrls.invoke(converter, testResource, urls);

        // Assert
        String namespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        int sourceCount = testResource.listProperties(ontModel.getProperty(namespace + "zdroj")).toList().size();
        assertEquals(3, sourceCount, "Should have 3 source properties");
    }

    @Test
    void getElementProperties_WithMultilingualProperties_ExtractsAllLanguages() throws Exception {
        // This test requires a more complex XML setup with multilingual content
        String multilingualXML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             name="Multilingual Model" id="ml-model">
                <folder name="Business" id="folder-business" type="business">
                    <element xsi:type="archimate:BusinessActor" name="Test Actor" id="element-1">
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
                </folder>
                <propertyDefinition identifier="prop-def-1" name="popis"/>
            </archimate:model>
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
        assertTrue(result.contains("Test"), "IRI should contain sanitized model name");
    }

    @Test
    void isOntologyNamespaceProperty_WithNamespaceProperty_ReturnsTrue() throws Exception {
        // Arrange
        propertyMapping.put("prop-1", "adresa lokálního katalogu dat");
        Method isOntologyNamespaceProperty = ArchiConverter.class.getDeclaredMethod(
                "isOntologyNamespaceProperty", String.class);
        isOntologyNamespaceProperty.setAccessible(true);

        // Act
        boolean result = (boolean) isOntologyNamespaceProperty.invoke(converter, "prop-1");

        // Assert
        assertTrue(result, "Should return true for namespace property");
    }

    @Test
    void isOntologyNamespaceProperty_WithNonNamespaceProperty_ReturnsFalse() throws Exception {
        // Arrange
        propertyMapping.put("prop-1", "popis");
        Method isOntologyNamespaceProperty = ArchiConverter.class.getDeclaredMethod(
                "isOntologyNamespaceProperty", String.class);
        isOntologyNamespaceProperty.setAccessible(true);

        // Act
        boolean result = (boolean) isOntologyNamespaceProperty.invoke(converter, "prop-1");

        // Assert
        assertFalse(result, "Should return false for non-namespace property");
    }

    // Integration test for complete element processing
    @Test
    void completeElementProcessing_WithComplexXML_CreatesCorrectStructure() throws Exception {
        // Arrange
        setupArchiDocument(COMPLEX_ARCHI_XML);

        // Act - Full conversion process
        invokePrivateMethod("buildPropertyMapping", new Class<?>[0]);
        invokePrivateMethod("processModelNameProperty", new Class<?>[0]);
        invokePrivateMethod("initializeTypeClasses", new Class<?>[0]);
        invokePrivateMethod("processElements", new Class<?>[0]);
        invokePrivateMethod("processRelationships", new Class<?>[0]);

        // Assert
        assertFalse(resourceMap.isEmpty(), "Should have created resources");
        assertTrue(resourceMap.containsKey("element-1"), "Should contain subject element");
        assertTrue(resourceMap.containsKey("element-2"), "Should contain object element");
        assertTrue(resourceMap.containsKey("rel-1"), "Should contain relationship");

        // Verify ontology namespace was set
        Field namespaceField = ArchiConverter.class.getDeclaredField("ontologyNamespace");
        namespaceField.setAccessible(true);
        String namespace = (String) namespaceField.get(converter);
        assertEquals("https://example.org/vocabulary/", namespace);

        // Verify resource types
        Resource subject = resourceMap.get("element-1");
        Resource object = resourceMap.get("element-2");

        String effectiveNamespace = (String) invokePrivateMethod("getEffectiveOntologyNamespace", new Class<?>[0]);
        assertTrue(subject.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_TSP)));
        assertTrue(object.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + TYP_TOP)));
    }
}
