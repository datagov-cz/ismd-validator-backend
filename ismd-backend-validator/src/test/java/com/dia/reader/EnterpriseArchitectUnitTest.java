package com.dia.reader;

import com.dia.converter.data.*;
import com.dia.converter.reader.ea.EnterpriseArchitectReader;
import com.dia.exceptions.FileParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.dia.constants.ArchiConstants.DEFAULT_NS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link EnterpriseArchitectReader}
 *</p>
 * @see EnterpriseArchitectReader
 */
@ExtendWith(MockitoExtension.class)
class EnterpriseArchitectUnitTest {

    @InjectMocks
    private EnterpriseArchitectReader reader;

    private byte[] validXmlBytes;

    @BeforeEach
    void setUp() throws IOException {
        ClassPathResource resource = new ClassPathResource("com/dia/minimal-ea.xml");
        validXmlBytes = Files.readAllBytes(Paths.get(resource.getURI()));
    }

    @Test
    void testReadXmiFromBytes_ValidFile_ShouldParseSuccessfully() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        assertNotNull(result);
        assertNotNull(result.getVocabularyMetadata());
        assertEquals("Příkladový slovník z metodiky popisu dat", result.getVocabularyMetadata().getName());

        assertFalse(result.getClasses().isEmpty());
        assertFalse(result.getProperties().isEmpty());
        assertFalse(result.getRelationships().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidXmlTestCases")
    void testReadXmiFromBytes_InvalidInputs_ShouldThrowException(byte[] input) {
        assertThrows(
                FileParsingException.class,
                () -> reader.readXmiFromBytes(input)
        );
    }

    @Test
    void testReadXmiFromBytes_NullInput_ShouldThrowException() {
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"typObjektu", "typSubjektu"})
    void testParsing_ClassTypes_ShouldParseCorrectly(String stereotype) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        String expectedType = "typSubjektu".equals(stereotype) ? "Subjekt práva" : "Objekt práva";

        boolean foundExpectedType = result.getClasses().stream()
                .anyMatch(c -> expectedType.equals(c.getType()));
        assertTrue(foundExpectedType, "Should find class with type: " + expectedType);
    }

    @ParameterizedTest
    @MethodSource("expectedClassData")
    void testParsing_SpecificClasses_ShouldHaveCorrectData(String className, String expectedType) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        ClassData classData = result.getClasses().stream()
                .filter(c -> className.equals(c.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(classData, "Should find class: " + className);
        assertEquals(expectedType, classData.getType());
        assertTrue(classData.hasValidData());
    }

    @ParameterizedTest
    @MethodSource("expectedPropertyData")
    void testParsing_SpecificProperties_ShouldHaveCorrectData(String propertyName, String expectedDomain) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        PropertyData property = result.getProperties().stream()
                .filter(p -> propertyName.equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(property, "Should find property: " + propertyName);
        if (expectedDomain != null) {
            assertEquals(expectedDomain, property.getDomain());
        }
        assertTrue(property.hasValidData());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sídlí na adrese", "vydává řidičský průkaz", "řídí vozidlo", "drží řidičský průkaz"})
    void testParsing_SpecificRelationships_ShouldExist(String relationshipName) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        boolean foundRelationship = result.getRelationships().stream()
                .anyMatch(r -> relationshipName.equals(r.getName()));
        assertTrue(foundRelationship, "Should find relationship: " + relationshipName);
    }

    @Test
    void testParsing_Inheritance_ShouldSetSuperClasses() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        // From the XML: "Řidič evidovaný v registru řidičů" extends "Řidič"
        ClassData childClass = result.getClasses().stream()
                .filter(c -> "Řidič evidovaný v registru řidičů".equals(c.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(childClass);
        assertEquals("Řidič", childClass.getSuperClass());

        // "Řidič" extends "Účastník provozu na pozemních komunikacích"
        ClassData parentClass = result.getClasses().stream()
                .filter(c -> "Řidič".equals(c.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(parentClass);
        assertEquals("Účastník provozu na pozemních komunikacích", parentClass.getSuperClass());
    }

    @Test
    void testParsing_VocabularyMetadata_ShouldExtractCorrectly() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        VocabularyMetadata metadata = result.getVocabularyMetadata();
        assertNotNull(metadata);
        assertEquals("Příkladový slovník z metodiky popisu dat", metadata.getName());

        // Should generate namespace if not present
        String namespace = metadata.getNamespace();
        assertTrue(namespace == null || namespace.isEmpty() || namespace.startsWith(DEFAULT_NS));
    }

    @Test
    void testParsing_BooleanFields_ShouldHandleCorrectly() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        // Check that boolean fields are properly handled in properties and relationships
        result.getProperties().forEach(property -> {
            assertValidBooleanField(property.getSharedInPPDF(), "sharedInPPDF");
            assertValidBooleanField(property.getIsPublic(), "isPublic");
        });

        result.getRelationships().forEach(relationship -> {
            assertValidBooleanField(relationship.getSharedInPPDF(), "sharedInPPDF");
            assertValidBooleanField(relationship.getIsPublic(), "isPublic");
        });
    }

    @Test
    void testParsing_CompleteWorkflow_ShouldProduceValidOntology() throws FileParsingException {
        long startTime = System.currentTimeMillis();
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);
        long endTime = System.currentTimeMillis();

        // Validate structure
        assertNotNull(result.getVocabularyMetadata());
        assertFalse(result.getClasses().isEmpty());
        assertFalse(result.getProperties().isEmpty());
        assertFalse(result.getRelationships().isEmpty());

        // Validate data quality
        result.getClasses().forEach(c -> assertTrue(c.hasValidData()));
        result.getProperties().forEach(p -> assertTrue(p.hasValidData()));
        result.getRelationships().stream()
                .filter(RelationshipData::hasValidData)
                .forEach(r -> assertNotNull(r.getName()));

        // Performance check
        assertTrue(endTime - startTime < 5000, "Parsing should complete quickly");

        System.out.printf("Parsed: %d classes, %d properties, %d relationships in %dms%n",
                result.getClasses().size(), result.getProperties().size(),
                result.getRelationships().size(), endTime - startTime);
    }

    // Test data providers

    static Stream<Arguments> invalidXmlTestCases() {
        return Stream.of(
                Arguments.of("invalid xml".getBytes(StandardCharsets.UTF_8), "Failed to parse XML"),
                Arguments.of(new byte[0], "Failed to parse XML"),
                Arguments.of(createXmlWithoutVocabularyPackage(), "No package with stereotype 'slovnikyPackage'")
        );
    }

    static Stream<Arguments> expectedClassData() {
        return Stream.of(
                Arguments.of("Adresa", "Objekt práva"),
                Arguments.of("Řidič", "Subjekt práva"),
                Arguments.of("Řidičský průkaz", "Objekt práva"),
                Arguments.of("Obecní úřad obce s rozšířenou působností ", "Subjekt práva"),
                Arguments.of("Vozidlo", "Objekt práva")
        );
    }

    static Stream<Arguments> expectedPropertyData() {
        return Stream.of(
                Arguments.of("Jméno držitele řidičského průkazu", "Řidičský průkaz"),
                Arguments.of("Příjmení držitele řidičského průkazu", "Řidičský průkaz"),
                Arguments.of("Název obecního úřadu obce s rozšířenou působností", "Obecní úřad obce s rozšířenou působností ")
        );
    }

    // Helper methods

    private void assertValidBooleanField(String value, String fieldName) {
        if (value != null) {
            assertTrue(value.equals("Yes") || value.equals("No") || !value.trim().isEmpty(),
                    "Boolean field '" + fieldName + "' should be null, 'Yes', 'No', or non-empty: " + value);
        }
    }

    private static byte[] createXmlWithoutVocabularyPackage() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <xmi:XMI xmlns:xmi="http://schema.omg.org/spec/XMI/2.1">
                <uml:Model name="TestModel">
                    <packagedElement xmi:type="uml:Package" xmi:id="pkg1" name="RegularPackage"/>
                </uml:Model>
                <xmi:Extension>
                    <elements>
                        <element xmi:idref="pkg1">
                            <properties stereotype="regularPackage"/>
                        </element>
                    </elements>
                </xmi:Extension>
            </xmi:XMI>
            """.getBytes(StandardCharsets.UTF_8);
    }
}