package com.dia.reader;

import com.dia.converter.data.*;
import com.dia.converter.reader.archi.ArchiReader;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link ArchiReader}
 *</p>
 * @see ArchiReader
 */
@ExtendWith(MockitoExtension.class)
class ArchiReaderUnitTest {

    @InjectMocks
    private ArchiReader reader;

    private String validXmlContent;

    @BeforeEach
    void setUp() throws IOException {
        // Set up MDC for logging
        MDC.put(LOG_REQUEST_ID, "test-request-123");

        ClassPathResource resource = new ClassPathResource("com/dia/minimal-archi.xml");
        validXmlContent = Files.readString(Paths.get(resource.getURI()));
    }

    @Test
    void testReadArchiFromString_ValidFile_ShouldParseSuccessfully() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        assertNotNull(result);
        assertNotNull(result.getVocabularyMetadata());
        assertEquals("DEMO Šablona pro popis dat Archi v1", result.getVocabularyMetadata().getName());
        assertEquals("https://data.dia.gov.cz", result.getVocabularyMetadata().getNamespace());

        assertFalse(result.getClasses().isEmpty());
        assertFalse(result.getProperties().isEmpty());
        assertFalse(result.getRelationships().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidXmlTestCases")
    void testReadArchiFromString_InvalidInputs_ShouldThrowException(String input) {
        assertThrows(
                FileParsingException.class,
                () -> reader.readArchiFromString(input)
        );
    }

    @Test
    void testReadArchiFromString_NullInput_ShouldThrowException() {
        assertThrows(FileParsingException.class, () -> reader.readArchiFromString(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"typ subjektu", "typ objektu"})
    void testExtractClasses_ByType_ShouldParseCorrectly(String elementType) throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        String expectedType = "typ subjektu".equals(elementType) ? "Subjekt práva" : "Objekt práva";

        boolean foundExpectedType = result.getClasses().stream()
                .anyMatch(c -> expectedType.equals(c.getType()));
        assertTrue(foundExpectedType, "Should find class with type: " + expectedType);
    }

    @ParameterizedTest
    @MethodSource("expectedClassData")
    void testExtractClasses_SpecificClasses_ShouldHaveCorrectData(String className, String expectedType, boolean shouldHaveDefinition) throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

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

    @ParameterizedTest
    @MethodSource("expectedPropertyData")
    void testExtractProperties_SpecificProperties_ShouldHaveCorrectData(String propertyName, String expectedDomain, boolean shouldHaveDescription) throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        PropertyData property = result.getProperties().stream()
                .filter(p -> propertyName.equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(property, "Should find property: " + propertyName);

        if (expectedDomain != null) {
            assertEquals(expectedDomain, property.getDomain());
        }

        if (shouldHaveDescription) {
            assertNotNull(property.getDescription(), "Property " + propertyName + " should have description");
            assertFalse(property.getDescription().trim().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"drží řidičský průkaz", "řídí vozidlo", "sídlí na adrese", "vydává řidičský průkaz"})
    void testExtractRelationships_SpecificRelationships_ShouldExist(String relationshipName) throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        boolean foundRelationship = result.getRelationships().stream()
                .anyMatch(r -> relationshipName.equals(r.getName()));
        assertTrue(foundRelationship, "Should find relationship: " + relationshipName);
    }

    @Test
    void testExtractHierarchies_ShouldExtractSpecializationRelationships() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        assertFalse(result.getHierarchies().isEmpty(), "Should extract hierarchy data");

        // Check for specific hierarchy relationships from the XML
        HierarchyData hierarchy = result.getHierarchies().stream()
                .filter(h -> "Řidič evidovaný v registru řidičů".equals(h.getSubClass()) &&
                        "Řidič".equals(h.getSuperClass()))
                .findFirst()
                .orElse(null);

        assertNotNull(hierarchy, "Should find hierarchy: Řidič evidovaný v registru řidičů -> Řidič");
        assertTrue(hierarchy.hasValidData());
        assertNotNull(hierarchy.getRelationshipName());
    }

    @Test
    void testProcessPropertyDomains_ShouldSetCorrectDomains() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        // Check composition relationships set property domains correctly
        PropertyData jmenoProperty = result.getProperties().stream()
                .filter(p -> "Jméno držitele řidičského průkazu".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(jmenoProperty);
        assertEquals("Řidičský průkaz", jmenoProperty.getDomain());

        PropertyData nazevProperty = result.getProperties().stream()
                .filter(p -> "Název obecního úřadu obce s rozšířenou působností".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(nazevProperty);
        assertEquals("Obecní úřad obce s rozšířenou působností", nazevProperty.getDomain());
    }

    @ParameterizedTest
    @MethodSource("propertyMappingTestCases")
    void testPropertyMapping_ShouldMapCorrectly(String propertyName, String expectedMappedValue) throws FileParsingException {
        reader.readArchiFromString(validXmlContent);

        // Verify property mapping contains expected mappings
        assertTrue(reader.getPropertyMapping().containsValue(expectedMappedValue),
                "Property mapping should contain: " + expectedMappedValue);
    }

    @Test
    void testExtractVocabularyMetadata_ShouldExtractCorrectly() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        VocabularyMetadata metadata = result.getVocabularyMetadata();
        assertNotNull(metadata);
        assertEquals("DEMO Šablona pro popis dat Archi v1", metadata.getName());
        assertEquals("https://data.dia.gov.cz", metadata.getNamespace());
    }

    @Test
    void testFiltering_TemplatePlaceholders_ShouldExclude() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        // Should not include template placeholders "Subjekt", "Objekt", "Vlastnost"
        assertFalse(result.getClasses().stream().anyMatch(c -> "Subjekt".equals(c.getName())));
        assertFalse(result.getClasses().stream().anyMatch(c -> "Objekt".equals(c.getName())));
        assertFalse(result.getProperties().stream().anyMatch(p -> "Vlastnost".equals(p.getName())));
    }

    @Test
    void testMultilingualSupport_ShouldHandleXmlLang() throws FileParsingException {
        // The XML contains both cs and en values for some properties
        // This should be handled correctly
        OntologyData result = reader.readArchiFromString(validXmlContent);

        // Verify parsing completes successfully with multilingual content
        assertNotNull(result);
        assertFalse(result.getProperties().isEmpty());
    }

    @Test
    void testCompleteWorkflow_ShouldProduceValidOntology() throws FileParsingException {
        long startTime = System.currentTimeMillis();
        OntologyData result = reader.readArchiFromString(validXmlContent);
        long endTime = System.currentTimeMillis();

        // Validate structure
        assertNotNull(result.getVocabularyMetadata());
        assertFalse(result.getClasses().isEmpty());
        assertFalse(result.getProperties().isEmpty());
        assertFalse(result.getRelationships().isEmpty());

        // Validate data quality
        result.getClasses().forEach(c -> {
            assertTrue(c.hasValidData(), "Class should have valid data: " + c.getName());
            assertNotNull(c.getName());
            assertNotNull(c.getType());
        });

        result.getProperties().forEach(p -> assertNotNull(p.getName()));

        result.getRelationships().forEach(r -> {
            if (r.hasValidData()) {
                assertNotNull(r.getName());
                assertNotNull(r.getDomain());
                assertNotNull(r.getRange());
            }
        });

        // Performance check
        assertTrue(endTime - startTime < 5000, "Parsing should complete quickly");

        System.out.printf("Parsed Archi: %d classes, %d properties, %d relationships, %d hierarchies in %dms%n",
                result.getClasses().size(), result.getProperties().size(),
                result.getRelationships().size(), result.getHierarchies().size(), endTime - startTime);
    }

    @Test
    void testMdcLogging_ShouldUseRequestId() throws FileParsingException {
        // Verify that MDC request ID is used in logging
        String testRequestId = "test-logging-456";
        MDC.put(LOG_REQUEST_ID, testRequestId);

        OntologyData result = reader.readArchiFromString(validXmlContent);

        assertNotNull(result);
        assertEquals(testRequestId, MDC.get(LOG_REQUEST_ID));
    }

    // ========== NEW TESTS FOR ENHANCED FUNCTIONALITY ==========

    @ParameterizedTest
    @MethodSource("booleanValueTestCases")
    void testCleanBooleanValue_ShouldNormalizeCorrectly(String input, String expected) throws Exception {
        // Access the private method using reflection for testing
        java.lang.reflect.Method method = ArchiReader.class.getDeclaredMethod("cleanBooleanValue", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(reader, input);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("provisionValueTestCases")
    void testCleanProvisionValue_ShouldCleanCorrectly(String input, String expected) throws Exception {
        // Access the private method using reflection for testing
        java.lang.reflect.Method method = ArchiReader.class.getDeclaredMethod("cleanProvisionValue", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(reader, input);
        assertEquals(expected, result);
    }

    @Test
    void testValidatePublicityConsistency_ShouldHandleInconsistencies() throws Exception {
        String testXml = createTestXmlWithPublicityData();

        OntologyData result = reader.readArchiFromString(testXml);
        assertNotNull(result);
    }

    @Test
    void testIdentifierValidation_ShouldValidateIRIs() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        result.getClasses().forEach(classData -> {
            if (classData.getIdentifier() != null) {
                // All preserved identifiers should be valid IRIs
                assertTrue(classData.getIdentifier().startsWith("http://") ||
                                classData.getIdentifier().startsWith("https://"),
                        "Identifier should be a valid IRI: " + classData.getIdentifier());
            }
        });

        result.getProperties().forEach(property -> {
            if (property.getIdentifier() != null) {
                assertTrue(property.getIdentifier().startsWith("http://") ||
                                property.getIdentifier().startsWith("https://"),
                        "Property identifier should be a valid IRI: " + property.getIdentifier());
            }
        });
    }

    @Test
    void testEnhancedPropertyMapping_ShouldMapStandardizedLabels() throws FileParsingException {
        reader.readArchiFromString(validXmlContent);

        assertTrue(reader.getPropertyMapping().containsValue("popis"));
        assertTrue(reader.getPropertyMapping().containsValue("definice"));
        assertTrue(reader.getPropertyMapping().containsValue("identifikátor"));
        assertTrue(reader.getPropertyMapping().containsValue("typ"));
        assertTrue(reader.getPropertyMapping().containsValue("zdroj"));
        assertTrue(reader.getPropertyMapping().containsValue("související-zdroj"));
    }

    @Test
    void testHierarchyExclusion_ShouldSkipTemplatePlaceholders() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        // Verify hierarchies don't include template placeholders
        result.getHierarchies().forEach(hierarchy -> {
            assertNotEquals("Subjekt", hierarchy.getSubClass());
            assertNotEquals("Objekt", hierarchy.getSubClass());
            assertNotEquals("Vlastnost", hierarchy.getSubClass());
            assertNotEquals("Subjekt", hierarchy.getSuperClass());
            assertNotEquals("Objekt", hierarchy.getSuperClass());
            assertNotEquals("Vlastnost", hierarchy.getSuperClass());
        });
    }

    @Test
    void testPropertyTypeDetection_ShouldIdentifyPropertyTypes() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        assertFalse(result.getProperties().isEmpty());

        result.getProperties().forEach(property -> {
            assertNotNull(property.getName());
            assertNotEquals("Vlastnost", property.getName());
        });
    }

    @Test
    void testRelationshipDataExtraction_ShouldHandleAllTypes() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        // Check that relationships have proper domain and range set
        result.getRelationships().forEach(relationship -> {
            if (relationship.hasValidData()) {
                assertNotNull(relationship.getDomain(), "Relationship should have domain: " + relationship.getName());
                assertNotNull(relationship.getRange(), "Relationship should have range: " + relationship.getName());
                assertNotNull(relationship.getRelationshipType(), "Relationship should have type: " + relationship.getName());
            }
        });
    }

    @Test
    void testSecurityFeatures_ShouldEnableXmlSecurity() {
        String xmlWithDtd = "<?xml version=\"1.0\"?><!DOCTYPE test SYSTEM \"test.dtd\"><test></test>";

        assertThrows(FileParsingException.class, () -> reader.readArchiFromString(xmlWithDtd));
    }

    @Test
    void testNamespaceExtraction_ShouldValidateUrls() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

        VocabularyMetadata metadata = result.getVocabularyMetadata();
        if (metadata.getNamespace() != null) {
            assertTrue(metadata.getNamespace().startsWith("http://") ||
                            metadata.getNamespace().startsWith("https://"),
                    "Namespace should be a valid URL: " + metadata.getNamespace());
        }
    }

    // ========== TEST DATA PROVIDERS ==========

    static Stream<String> invalidXmlTestCases() {
        return Stream.of(
                "invalid xml content",
                "",
                "<invalid><unclosed>",
                "<?xml version='1.0'?<root></root>"
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

    static Stream<Arguments> propertyMappingTestCases() {
        return Stream.of(
                Arguments.of("popis", "popis"),
                Arguments.of("definice", "definice"),
                Arguments.of("zdroj", "zdroj"),
                Arguments.of("související zdroj", "související-zdroj"),
                Arguments.of("alternativní název", "alternativní-název"),
                Arguments.of("identifikátor", "identifikátor"),
                Arguments.of("typ", "typ"),
                Arguments.of("je pojem sdílen v PPDF?", "je-sdílen-v-ppdf"),
                Arguments.of("je pojem veřejný?", "je-pojem-veřejný")
        );
    }

    static Stream<Arguments> booleanValueTestCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("  ", null),
                Arguments.of("true", "true"),
                Arguments.of("TRUE", "true"),
                Arguments.of("True", "true"),
                Arguments.of("ano", "true"),
                Arguments.of("ANO", "true"),
                Arguments.of("yes", "true"),
                Arguments.of("false", "false"),
                Arguments.of("FALSE", "false"),
                Arguments.of("False", "false"),
                Arguments.of("ne", "false"),
                Arguments.of("NE", "false"),
                Arguments.of("no", "false"),
                Arguments.of("maybe", "maybe"), // Non-standard values preserved
                Arguments.of("  true  ", "true"),
                Arguments.of("  false  ", "false")
        );
    }

    static Stream<Arguments> provisionValueTestCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("  ", null),
                Arguments.of("null", null),
                Arguments.of("NULL", null),
                Arguments.of("n/a", null),
                Arguments.of("N/A", null),
                Arguments.of("není", null),
                Arguments.of("NENÍ", null),
                Arguments.of("none", null),
                Arguments.of("NONE", null),
                Arguments.of("valid provision", "valid provision"),
                Arguments.of("  valid provision  ", "valid provision")
        );
    }

    // ========== HELPER METHODS ==========

    private String createTestXmlWithPublicityData() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xmlns:archimate="http://www.archimatetool.com/archimate"
                           name="Test Model">
                <folder name="Business" id="business-folder" type="business">
                    <element id="test-element" xsi:type="BusinessObject">
                        <name>Test Property</name>
                        <properties>
                            <property propertyDefinitionRef="je-verejny-prop">
                                <value>true</value>
                            </property>
                            <property propertyDefinitionRef="ustanoveni-prop">
                                <value>Some privacy provision</value>
                            </property>
                            <property propertyDefinitionRef="typ-prop">
                                <value>typ vlastnosti</value>
                            </property>
                        </properties>
                    </element>
                </folder>
                <propertyDefinition id="je-verejny-prop">
                    <name>je pojem veřejný?</name>
                </propertyDefinition>
                <propertyDefinition id="ustanoveni-prop">
                    <name>ustanovení dokládající neveřejnost</name>
                </propertyDefinition>
                <propertyDefinition id="typ-prop">
                    <name>typ</name>
                </propertyDefinition>
            </archimate:model>
            """;
    }
}