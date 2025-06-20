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

import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;
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
        assertTrue(property.hasValidData());

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
    void testProcessInheritanceRelationships_ShouldSetSuperClasses() throws FileParsingException {
        OntologyData result = reader.readArchiFromString(validXmlContent);

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

        result.getProperties().forEach(p -> {
            assertTrue(p.hasValidData(), "Property should have valid data: " + p.getName());
            assertNotNull(p.getName());
        });

        result.getRelationships().forEach(r -> {
            if (r.hasValidData()) {
                assertNotNull(r.getName());
                assertNotNull(r.getDomain());
                assertNotNull(r.getRange());
            }
        });

        // Performance check
        assertTrue(endTime - startTime < 5000, "Parsing should complete quickly");

        System.out.printf("Parsed Archi: %d classes, %d properties, %d relationships in %dms%n",
                result.getClasses().size(), result.getProperties().size(),
                result.getRelationships().size(), endTime - startTime);
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

    // Test data providers

    static Stream<Arguments> invalidXmlTestCases() {
        return Stream.of(
                Arguments.of("Invalid XML syntax", "invalid xml content", "zpracování"),
                Arguments.of("Empty string", "", "zpracování"),
                Arguments.of("Malformed XML", "<invalid><unclosed>", "zpracování"),
                Arguments.of("XML without model", "<?xml version='1.0'?><root></root>", "zpracování")
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
}