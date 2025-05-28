package com.dia.converter;

import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import com.dia.exporter.JsonExporter;
import com.dia.exporter.TurtleExporter;
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
class ArchiConverterUnitTest {

    private ArchiConverter converter;

    @Mock
    private JsonExporter jsonExporter;

    @Mock
    private TurtleExporter turtleExporter;

    private String minimalArchiXML;
    private String completeArchiXML;
    private String invalidArchiXML;

    @BeforeEach
    void setUp() throws IOException {
        converter = new ArchiConverter();

        // Load test XML files
        minimalArchiXML = loadTestFile("minimal-archi.xml");
        completeArchiXML = loadTestFile("complete-archi.xml");
        invalidArchiXML = "<not-valid-xml>";
    }

    private String loadTestFile(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("/com/dia/" + filename, getClass());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
        Map<String, String> propertyMapping = (Map<String, String>) propertyMappingField.get(converter);
        assertFalse(propertyMapping.isEmpty(), "Property mappings should be created");
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
        OntModel ontModel = (OntModel) ontModelField.get(converter);

        // Check that the model contains expected resources
        assertFalse(ontModel.isEmpty(), "Model should not be empty");

        // Check resource map
        Field resourceMapField = ArchiConverter.class.getDeclaredField("resourceMap");
        resourceMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Resource> resourceMap = (Map<String, Resource>) resourceMapField.get(converter);
        assertFalse(resourceMap.isEmpty(), "Resource map should not be empty");

        // Check ontology resource was created
        assertTrue(resourceMap.containsKey("ontology"), "Ontology resource should be created");
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
        Map<String, Resource> resourceMap = (Map<String, Resource>) resourceMapField.get(converter);

        // Verify real element ID exists (complete-archi.xml, line 12 identifier)
        assertTrue(resourceMap.containsKey("id-cd39b4fc55534b9ca590187588b9d082"), "Expected element should exist");

        // Check model contains expected triples
        Field ontModelField = ArchiConverter.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        OntModel ontModel = (OntModel) ontModelField.get(converter);

        // Verify the model contains expected patterns
        assertTrue(ontModel.contains(null, RDF.type, (RDFNode) null), "Model should contain type statements");
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
}
