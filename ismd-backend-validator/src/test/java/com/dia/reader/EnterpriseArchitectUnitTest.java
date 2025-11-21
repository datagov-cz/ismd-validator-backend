package com.dia.reader;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.exceptions.FileParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;
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

    private String validXmlContent;
    private byte[] validXmlBytes;

    @BeforeEach
    void setUp() throws IOException {
        MDC.put(LOG_REQUEST_ID, "test-request-123");

        // Load the valid XML content
        ClassPathResource resource = new ClassPathResource("/com/dia/minimal-ea.xml");
        validXmlContent = Files.readString(Paths.get(resource.getURI()));
        validXmlBytes = validXmlContent.getBytes(StandardCharsets.UTF_8);
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
        assertFalse(result.getHierarchies().isEmpty());
    }

    @Test
    void testReadXmiFromBytes_NullInput_ShouldThrowException() {
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(null));
    }

    @Test
    void testReadXmiFromBytes_EmptyInput_ShouldThrowException() {
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(new byte[0]));
    }

    // ========== ENCODING DETECTION TESTS ==========

    @Test
    void testEncodingDetection_UTF8_ShouldParseSuccessfully() throws FileParsingException {
        byte[] utf8Bytes = validXmlContent.getBytes(StandardCharsets.UTF_8);
        OntologyData result = reader.readXmiFromBytes(utf8Bytes);
        assertNotNull(result);
    }

    @Test
    void testEncodingDetection_Windows1252_ShouldParseSuccessfully() throws FileParsingException {
        // Create content with Windows-1252 specific characters
        String windows1252Content = validXmlContent.replace("Příkladový", "Príkladový");
        byte[] windows1252Bytes = windows1252Content.getBytes(StandardCharsets.UTF_8);

        OntologyData result = reader.readXmiFromBytes(windows1252Bytes);
        assertNotNull(result);
    }

    @Test
    void testEncodingDetection_InvalidXml_ShouldThrowException() {
        byte[] invalidBytes = "invalid xml content".getBytes(StandardCharsets.UTF_8);
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(invalidBytes));
    }

    // ========== VOCABULARY PACKAGE DETECTION TESTS ==========

    @Test
    void testVocabularyPackageDetection_ValidPackage_ShouldFindPackage() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);
        assertNotNull(result.getVocabularyMetadata());
        assertNotNull(result.getVocabularyMetadata().getName());
    }

    @Test
    void testVocabularyPackageDetection_NoVocabularyPackage_ShouldThrowException() {
        String xmlWithoutVocabularyPackage = createXmlWithoutVocabularyPackage();
        byte[] bytes = xmlWithoutVocabularyPackage.getBytes(StandardCharsets.UTF_8);

        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(bytes));
    }

    // ========== METADATA EXTRACTION TESTS ==========

    @Test
    void testExtractVocabularyMetadata_ShouldExtractCorrectly() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        VocabularyMetadata metadata = result.getVocabularyMetadata();
        assertNotNull(metadata);
        assertEquals("Příkladový slovník z metodiky popisu dat", metadata.getName());
        assertNotNull(metadata.getNamespace());
    }

    // ========== CLASS EXTRACTION TESTS ==========

    @ParameterizedTest
    @ValueSource(strings = {"typObjektu", "typSubjektu"})
    void testExtractClasses_ByStereotype_ShouldParseCorrectly(String stereotype) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        String expectedType = "typSubjektu".equals(stereotype) ? "Subjekt práva" : "Objekt práva";

        boolean foundExpectedType = result.getClasses().stream()
                .anyMatch(c -> expectedType.equals(c.getType()));
        assertTrue(foundExpectedType, "Should find class with type: " + expectedType);
    }

    @ParameterizedTest
    @MethodSource("expectedClassData")
    void testExtractClasses_SpecificClasses_ShouldHaveCorrectData(String className, String expectedType, boolean shouldHaveDefinition) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        ClassData classData = result.getClasses().stream()
                .filter(c -> className.equals(c.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(classData, "Should find class: " + className);
        assertEquals(expectedType, classData.getType());
        assertTrue(classData.hasValidData());

        if (shouldHaveDefinition) {
            assertNotNull(classData.getDefinition(), "Class " + className + " should have definition");
            assertFalse(classData.getDefinition().trim().isEmpty());
        }
    }

    // ========== PROPERTY EXTRACTION TESTS ==========

    @Test
    void testExtractProperties_ShouldParseTypVlastnosti() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        assertFalse(result.getProperties().isEmpty());

        PropertyData property = result.getProperties().stream()
                .filter(p -> "Jméno držitele řidičského průkazu".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(property);
        assertNotNull(property.getDescription());
    }

    @ParameterizedTest
    @MethodSource("expectedPropertyData")
    void testExtractProperties_SpecificProperties_ShouldHaveCorrectData(String propertyName, String expectedDomain, boolean shouldHaveDescription) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        PropertyData property = result.getProperties().stream()
                .filter(p -> propertyName.equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(property, "Should find property: " + propertyName);

        if (expectedDomain != null) {
            assertEquals(expectedDomain.trim(), property.getDomain().trim());
        }

        if (shouldHaveDescription) {
            assertNotNull(property.getDescription(), "Property " + propertyName + " should have description");
            assertFalse(property.getDescription().trim().isEmpty());
        }
    }

    // ========== RELATIONSHIP EXTRACTION TESTS ==========

    @ParameterizedTest
    @ValueSource(strings = {"drží řidičský průkaz", "řídí vozidlo", "sídlí na adrese", "vydává řidičský průkaz"})
    void testExtractRelationships_SpecificRelationships_ShouldExist(String relationshipName) throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        boolean foundRelationship = result.getRelationships().stream()
                .anyMatch(r -> relationshipName.equals(r.getName()));
        assertTrue(foundRelationship, "Should find relationship: " + relationshipName);
    }

    @Test
    void testExtractRelationships_ShouldSetDomainAndRange() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        result.getRelationships().forEach(relationship -> {
            assertNotNull(relationship.getDomain(), "Relationship should have domain: " + relationship.getName());
            assertNotNull(relationship.getRange(), "Relationship should have range: " + relationship.getName());
        });
    }

    // ========== HIERARCHY EXTRACTION TESTS ==========

    @Test
    void testExtractHierarchies_ShouldExtractInheritanceRelationships() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        assertFalse(result.getHierarchies().isEmpty(), "Should extract hierarchy data");

        HierarchyData hierarchy = result.getHierarchies().stream()
                .filter(h -> "Řidič evidovaný v registru řidičů".equals(h.getSubClass()) &&
                        "Řidič".equals(h.getSuperClass()))
                .findFirst()
                .orElse(null);

        assertNotNull(hierarchy, "Should find hierarchy: Řidič evidovaný v registru řidičů -> Řidič");
        assertTrue(hierarchy.hasValidData());
    }

    @Test
    void testExtractHierarchies_ShouldEnrichWithConnectorData() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        result.getHierarchies().forEach(hierarchy -> {
            assertNotNull(hierarchy.getRelationshipName(),
                    "Hierarchy should have relationship name: " + hierarchy.getSubClass() + " -> " + hierarchy.getSuperClass());
        });
    }

    // ========== TAG VALUE EXTRACTION TESTS ==========

    @ParameterizedTest
    @MethodSource("tagCleaningTestCases")
    void testCleanTagValue_ShouldCleanCorrectly(String input, String expected) {
        String result = reader.cleanTagValue(input);
        assertEquals(expected, result);
    }

    @Test
    void testGetTagValueByPattern_ShouldMatchPatterns() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        boolean foundDescription = result.getClasses().stream()
                .anyMatch(c -> c.getDescription() != null && !c.getDescription().trim().isEmpty());
        assertTrue(foundDescription, "Should find descriptions using patterns");

        boolean foundSource = result.getClasses().stream()
                .anyMatch(c -> c.getSource() != null && !c.getSource().trim().isEmpty());
        assertTrue(foundSource, "Should find sources using patterns");
    }

    // ========== SOURCE VALIDATION TESTS ==========

    @ParameterizedTest
    @MethodSource("sourceValidationTestCases")
    void testSourceValidation_ShouldValidateCorrectly(String input, String expected) throws Exception {
        java.lang.reflect.Method method = EnterpriseArchitectReader.class.getDeclaredMethod("validateAndCleanSourceValue", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(reader, input);
        assertEquals(expected, result);
    }

    // ========== CONNECTOR PROCESSING TESTS ==========

    @Test
    void testProcessConnectors_AssociationType_ShouldCreateRelationships() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        boolean foundAssociation = result.getRelationships().stream()
                .anyMatch(r -> r.getName() != null);
        assertTrue(foundAssociation, "Should find association relationships");
    }

    @Test
    void testProcessConnectors_GeneralizationType_ShouldCreateHierarchies() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        boolean foundGeneralization = result.getHierarchies().stream()
                .anyMatch(h -> h.getSubClass() != null && h.getSuperClass() != null);
        assertTrue(foundGeneralization, "Should find generalization relationships");
    }

    @Test
    void testProcessConnectors_AggregationType_ShouldSetPropertyDomains() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        PropertyData property = result.getProperties().stream()
                .filter(p -> "Jméno držitele řidičského průkazu".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(property);
        assertEquals("Řidičský průkaz", property.getDomain());
    }

    // ========== ERROR HANDLING TESTS ==========

    @ParameterizedTest
    @MethodSource("invalidXmlTestCases")
    void testReadXmiFromBytes_InvalidInputs_ShouldThrowException(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(bytes));
    }

    @Test
    void testReadXmiFromBytes_MalformedXml_ShouldThrowException() {
        String malformedXml = "<root><unclosed></root>";
        byte[] bytes = malformedXml.getBytes(StandardCharsets.UTF_8);
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(bytes));
    }

    @Test
    void testReadXmiFromBytes_XmlWithoutRequiredElements_ShouldThrowException() {
        String minimalXml = "<?xml version='1.0'?><root></root>";
        byte[] bytes = minimalXml.getBytes(StandardCharsets.UTF_8);
        assertThrows(FileParsingException.class, () -> reader.readXmiFromBytes(bytes));
    }

    // ========== PERFORMANCE TESTS ==========

    @Test
    void testReadXmiFromBytes_Performance_ShouldCompleteQuickly() throws FileParsingException {
        long startTime = System.currentTimeMillis();

        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertNotNull(result);
        assertTrue(duration < 5000, "Parsing should complete within 5 seconds, took: " + duration + "ms");

        System.out.printf("EA Parsing completed in %dms: %d classes, %d properties, %d relationships, %d hierarchies%n",
                duration, result.getClasses().size(), result.getProperties().size(),
                result.getRelationships().size(), result.getHierarchies().size());
    }

    // ========== DATA QUALITY TESTS ==========

    @Test
    void testDataQuality_AllClassesShouldHaveValidData() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        result.getClasses().forEach(classData -> {
            assertTrue(classData.hasValidData(), "Class should have valid data: " + classData.getName());
            assertNotNull(classData.getName(), "Class should have name");
            assertNotNull(classData.getType(), "Class should have type");
        });
    }

    @Test
    void testDataQuality_AllPropertiesShouldHaveNames() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        result.getProperties().forEach(property -> {
            assertNotNull(property.getName(), "Property should have name");
            assertFalse(property.getName().trim().isEmpty(), "Property name should not be empty");
        });
    }

    @Test
    void testDataQuality_AllRelationshipsShouldBeValid() throws FileParsingException {
        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        result.getRelationships().forEach(relationship -> {
            assertNotNull(relationship.getName(), "Valid relationship should have name");
            assertNotNull(relationship.getDomain(), "Valid relationship should have domain");
            assertNotNull(relationship.getRange(), "Valid relationship should have range");
        });
    }

    // ========== LOGGING TESTS ==========

    @Test
    void testMdcLogging_ShouldUseRequestId() throws FileParsingException {
        String testRequestId = "test-logging-456";
        MDC.put(LOG_REQUEST_ID, testRequestId);

        OntologyData result = reader.readXmiFromBytes(validXmlBytes);

        assertNotNull(result);
        assertEquals(testRequestId, MDC.get(LOG_REQUEST_ID));
    }

    // ========== TEST DATA PROVIDERS ==========

    static Stream<String> invalidXmlTestCases() {
        return Stream.of(
                "invalid xml content",
                "",
                "<invalid><unclosed>",
                "<?xml version='1.0'?<root></root>",
                "not xml at all",
                "<xml><element></xml>"  // mismatched tags
        );
    }

    static Stream<Arguments> expectedClassData() {
        return Stream.of(
                Arguments.of("Řidič", "Subjekt práva", true),
                Arguments.of("Řidičský průkaz", "Objekt práva", true),
                Arguments.of("Obecní úřad obce s rozšířenou působností", "Subjekt práva", false),
                Arguments.of("Vozidlo", "Objekt práva", true),
                Arguments.of("Adresa", "Objekt práva", true),
                Arguments.of("Účastník provozu na pozemních komunikacích", "Subjekt práva", true)
        );
    }

    static Stream<Arguments> expectedPropertyData() {
        return Stream.of(
                Arguments.of("Jméno držitele řidičského průkazu", "Řidičský průkaz", true),
                Arguments.of("Příjmení držitele řidičského průkazu", "Řidičský průkaz", true),
                Arguments.of("Název obecního úřadu obce s rozšířenou působností", "Obecní úřad obce s rozšířenou působností", true)
        );
    }

    static Stream<Arguments> tagCleaningTestCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("  ", null),
                Arguments.of("simple text", "simple text"),
                Arguments.of("text with #NOTES# extra info", "text with"),
                Arguments.of("&lt;tag&gt;content&lt;/tag&gt;", "<tag>content</tag>"),
                Arguments.of("&amp;escaped&amp;", "&escaped&"),
                Arguments.of("&#xA;line break", "line break"),
                Arguments.of("  trimmed  ", "trimmed"),
                Arguments.of("multiple#NOTES#notes here", "multiple")
        );
    }

    static Stream<Arguments> sourceValidationTestCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("  ", null),
                Arguments.of("https://www.example.com", "https://www.example.com"),
                Arguments.of("https://first.com;https://second.com", "https://first.com;https://second.com")
        );
    }

    // ========== HELPER METHODS ==========

    private String createXmlWithoutVocabularyPackage() {
        return """
            <?xml version='1.0' encoding='utf-8' ?>
            <xmi:XMI xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmi:version="2.1">
                <uml:Model xmi:type="uml:Model" name="EA_Model" visibility="public">
                    <packagedElement xmi:type="uml:Package" xmi:id="EAPK_TEST" name="Regular Package" visibility="public">
                    </packagedElement>
                </uml:Model>
                <xmi:Extension extender="Enterprise Architect" extenderID="6.5">
                    <elements>
                        <element xmi:idref="EAPK_TEST" xmi:type="uml:Package" name="Regular Package" scope="public">
                            <properties stereotype="regularPackage"/>
                        </element>
                    </elements>
                </xmi:Extension>
            </xmi:XMI>
            """;
    }
}