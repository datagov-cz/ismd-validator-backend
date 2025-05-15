package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ConversionException;
import dia.ismd.common.exceptions.FileParsingException;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ArchiConvertorUnitTest {

    private ArchiConvertor convertor;

    @Mock
    private JSONExporter jsonExporter;

    @Mock
    private TurtleExporter turtleExporter;

    private String minimalArchiXML;
    private String completeArchiXML;
    private String invalidArchiXML;

    @BeforeEach
    void setUp() throws IOException {
        convertor = new ArchiConvertor();

        // Load test XML files
        minimalArchiXML = loadTestFile("minimal-archi.xml");
        completeArchiXML = loadTestFile("complete-archi.xml");
        invalidArchiXML = "<not-valid-xml>";
    }

    private String loadTestFile(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(filename, getClass());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void parseFromString_ValidXml_SuccessfullyParses() throws Exception {
        // Act
        convertor.parseFromString(minimalArchiXML);

        // Assert
        Field archiDocField = ArchiConvertor.class.getDeclaredField("archiDoc");
        archiDocField.setAccessible(true);
        assertNotNull(archiDocField.get(convertor), "Document should be parsed");

        // Verify property mappings were created
        Field propertyMappingField = ArchiConvertor.class.getDeclaredField("propertyMapping");
        propertyMappingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> propertyMapping = (Map<String, String>) propertyMappingField.get(convertor);
        assertFalse(propertyMapping.isEmpty(), "Property mappings should be created");
    }

    @Test
    void parseFromString_InvalidXml_ThrowsException() {
        // Act & Assert
        assertThrows(FileParsingException.class, () ->
                        convertor.parseFromString(invalidArchiXML),
                "Should throw FileParsingException for invalid XML"
        );
    }

    @Test
    void parseFromString_NullContent_ThrowsException() {
        // Act & Assert
        assertThrows(FileParsingException.class, () ->
                        convertor.parseFromString(null),
                "Should throw FileParsingException for null content"
        );
    }

    @Test
    void convert_WithParsedDocument_CreatesOntologyModel() throws Exception {
        // Arrange
        convertor.parseFromString(completeArchiXML);

        // Act
        convertor.convert();

        // Assert
        Field ontModelField = ArchiConvertor.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        OntModel ontModel = (OntModel) ontModelField.get(convertor);

        // Check that the model contains expected resources
        assertFalse(ontModel.isEmpty(), "Model should not be empty");

        // Check resource map
        Field resourceMapField = ArchiConvertor.class.getDeclaredField("resourceMap");
        resourceMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Resource> resourceMap = (Map<String, Resource>) resourceMapField.get(convertor);
        assertFalse(resourceMap.isEmpty(), "Resource map should not be empty");

        // Check ontology resource was created
        assertTrue(resourceMap.containsKey("ontology"), "Ontology resource should be created");
    }

    @Test
    void convert_WithoutParsedDocument_ThrowsException() {
        // Act & Assert
        assertThrows(ConversionException.class, () ->
                        convertor.convert(),
                "Should throw ConversionException when document not parsed"
        );
    }

    @Test
    void exportToJson_AfterConversion_ReturnsJsonString() throws Exception {
        // Arrange
        convertor.parseFromString(completeArchiXML);
        convertor.convert();

        // Act
        String jsonOutput = convertor.exportToJson();

        // Assert
        assertNotNull(jsonOutput, "JSON output should not be null");
        assertTrue(jsonOutput.startsWith("{"), "JSON should start with {");
        assertTrue(jsonOutput.endsWith("}"), "JSON should end with }");
    }

    @Test
    void exportToTurtle_AfterConversion_ReturnsTurtleString() throws Exception {
        // Arrange
        convertor.parseFromString(completeArchiXML);
        convertor.convert();

        // Act
        String turtleOutput = convertor.exportToTurtle();

        // Assert
        assertNotNull(turtleOutput, "Turtle output should not be null");
        assertTrue(turtleOutput.contains("PREFIX"), "Turtle should contain PREFIX declarations");
    }

    @Test
    void fullConversionPipeline_ValidInput_SuccessfulConversion() throws Exception {
        // This test verifies the entire processing pipeline
        // Arrange, Act, Assert
        convertor.parseFromString(completeArchiXML);
        convertor.convert();

        String jsonOutput = convertor.exportToJson();
        assertNotNull(jsonOutput, "JSON output should be produced");

        String turtleOutput = convertor.exportToTurtle();
        assertNotNull(turtleOutput, "Turtle output should be produced");
    }

    @Test
    void processElements_ContainsExpectedResources() throws Exception {
        // Arrange
        convertor.parseFromString(completeArchiXML);

        // Act
        convertor.convert();

        // Assert using reflection to check for specific resources
        Field resourceMapField = ArchiConvertor.class.getDeclaredField("resourceMap");
        resourceMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Resource> resourceMap = (Map<String, Resource>) resourceMapField.get(convertor);

        // Verify real element ID exists (complete-archi.xml, line 12 identifier)
        assertTrue(resourceMap.containsKey("id-cd39b4fc55534b9ca590187588b9d082"), "Expected element should exist");

        // Check model contains expected triples
        Field ontModelField = ArchiConvertor.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        OntModel ontModel = (OntModel) ontModelField.get(convertor);

        // Verify the model contains expected patterns
        assertTrue(ontModel.contains((Resource) null, RDF.type, (RDFNode) null), "Model should contain type statements");
    }

    @Test
    void parseFromString_WithDTD_HandlesSecurely() {
        // Test that XML with DTD references is handled securely
        String xmlWithDtd = "<?xml version=\"1.0\"?><!DOCTYPE test [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root>&xxe;</root>";

        // Should not throw exception but should handle DTD securely
        assertThrows(FileParsingException.class, () ->
                        convertor.parseFromString(xmlWithDtd),
                "Should handle DTD securely"
        );
    }



}
