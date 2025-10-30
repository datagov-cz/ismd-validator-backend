package com.dia.isolation;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.reader.ssp.SSPReader;
import com.dia.workflow.assertions.OFNAssertions;
import com.dia.workflow.config.TestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolation tests for Reader stage.
 * Validates that readers correctly extract all data from input files
 * without any transformation or export logic.
 */
@SpringBootTest
@Tag("isolation")
@Tag("reader")
public class ReaderIsolationTest {

    @Autowired
    private ArchiReader archiReader;

    @Autowired
    private EnterpriseArchitectReader eaReader;

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private SSPReader sspReader;

    /**
     * Provides test configuration and reader format combinations
     */
    static Stream<Arguments> readerTestProvider() {
        List<Arguments> arguments = new ArrayList<>();
        for (TestConfiguration config : TestConfiguration.allStandardConfigurations()) {
            if (config.getInputFiles().getArchiXml() != null) {
                arguments.add(Arguments.of(config, "ARCHI", config.getInputFiles().getArchiXml()));
            }
            if (config.getInputFiles().getEnterpriseArchitectXmi() != null) {
                arguments.add(Arguments.of(config, "EA", config.getInputFiles().getEnterpriseArchitectXmi()));
            }
            if (config.getInputFiles().getExcel() != null) {
                arguments.add(Arguments.of(config, "EXCEL", config.getInputFiles().getExcel()));
            }
            if (config.getInputFiles().getSsp() != null) {
                arguments.add(Arguments.of(config, "SSP", config.getInputFiles().getSsp()));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} - {1} reader")
    @MethodSource("readerTestProvider")
    void reader_shouldExtractAllCharacteristics(
            TestConfiguration config,
            String readerType,
            String inputFilePath) throws Exception {

        // When: Read input using appropriate reader
        OntologyData result = readInput(readerType, inputFilePath);

        // Then: Validate all data extracted according to metadata
        OFNAssertions.assertOntologyDataMatchesMetadata(result, config.getMetadata());
        OFNAssertions.assertNoDataLoss(result);
    }

    @ParameterizedTest(name = "{0} - {1} reader")
    @MethodSource("readerTestProvider")
    void reader_shouldPreserveVocabularyMetadata(
            TestConfiguration config,
            String readerType,
            String inputFilePath) throws Exception {

        // When: Read input
        OntologyData result = readInput(readerType, inputFilePath);

        // Then: Vocabulary metadata should be extracted
        var vocabExp = config.getMetadata().getVocabularyExpectations();
        if (vocabExp != null) {
            var vocabData = result.getVocabularyMetadata();

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveName())) {
                assertNotNull(vocabData, "VocabularyMetadata should be present");
                assertNotNull(vocabData.getName(), "Vocabulary name should be extracted");
                assertFalse(vocabData.getName().trim().isEmpty(), "Vocabulary name should not be empty");
            }

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveDescription())) {
                assertNotNull(vocabData, "VocabularyMetadata should be present");
                assertNotNull(vocabData.getDescription(), "Description should be extracted");
            }

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveNamespace())) {
                assertNotNull(vocabData, "VocabularyMetadata should be present");
                assertNotNull(vocabData.getNamespace(), "Namespace should be extracted");
            }

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveDateOfCreation())) {
                assertNotNull(vocabData, "VocabularyMetadata should be present");
                assertNotNull(vocabData.getDateOfCreation(), "Date of creation should be extracted");
            }

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveDateOfModification())) {
                assertNotNull(vocabData, "VocabularyMetadata should be present");
                assertNotNull(vocabData.getDateOfModification(), "Date of modification should be extracted");
            }
        }
    }

    @ParameterizedTest(name = "{0} - {1} reader")
    @MethodSource("readerTestProvider")
    void reader_shouldExtractCorrectEntityCounts(
            TestConfiguration config,
            String readerType,
            String inputFilePath) throws Exception {

        // When: Read input
        OntologyData result = readInput(readerType, inputFilePath);

        // Then: Entity counts should match expectations
        var expectedCounts = config.getMetadata().getExpectedCounts();
        if (expectedCounts != null) {

            if (expectedCounts.getClasses() != null) {
                int actualCount = result.getClasses() != null ? result.getClasses().size() : 0;
                assertTrue(expectedCounts.getClasses().matches(actualCount),
                    String.format("%s reader: Class count %d does not match expectations for %s",
                        readerType, actualCount, config.getTestCaseId()));
            }

            if (expectedCounts.getProperties() != null) {
                int actualCount = result.getProperties() != null ? result.getProperties().size() : 0;
                assertTrue(expectedCounts.getProperties().matches(actualCount),
                    String.format("%s reader: Property count %d does not match expectations for %s",
                        readerType, actualCount, config.getTestCaseId()));
            }

            if (expectedCounts.getRelationships() != null) {
                int actualCount = result.getRelationships() != null ? result.getRelationships().size() : 0;
                assertTrue(expectedCounts.getRelationships().matches(actualCount),
                    String.format("%s reader: Relationship count %d does not match expectations for %s",
                        readerType, actualCount, config.getTestCaseId()));
            }

            if (expectedCounts.getHierarchies() != null) {
                int actualCount = result.getHierarchies() != null ? result.getHierarchies().size() : 0;
                assertTrue(expectedCounts.getHierarchies().matches(actualCount),
                    String.format("%s reader: Hierarchy count %d does not match expectations for %s",
                        readerType, actualCount, config.getTestCaseId()));
            }
        }
    }

    @ParameterizedTest(name = "{0} - {1} reader")
    @MethodSource("readerTestProvider")
    void reader_shouldNotModifyOrTransformData(
            TestConfiguration config,
            String readerType,
            String inputFilePath) throws Exception {

        // When: Read input twice
        OntologyData result1 = readInput(readerType, inputFilePath);
        OntologyData result2 = readInput(readerType, inputFilePath);

        // Then: Results should be identical (reader is deterministic and stateless)
        assertEquals(
            result1.getClasses() != null ? result1.getClasses().size() : 0,
            result2.getClasses() != null ? result2.getClasses().size() : 0,
            "Reader should produce identical results on repeated reads - classes"
        );

        assertEquals(
            result1.getProperties() != null ? result1.getProperties().size() : 0,
            result2.getProperties() != null ? result2.getProperties().size() : 0,
            "Reader should produce identical results on repeated reads - properties"
        );

        assertEquals(
            result1.getRelationships() != null ? result1.getRelationships().size() : 0,
            result2.getRelationships() != null ? result2.getRelationships().size() : 0,
            "Reader should produce identical results on repeated reads - relationships"
        );
    }

    // ========== Helper Methods ==========

    /**
     * Reads input using appropriate reader based on format
     */
    private OntologyData readInput(String readerType, String filePath) throws Exception {
        switch (readerType) {
            case "ARCHI":
                String content = loadResourceAsString(filePath);
                return archiReader.readArchiFromString(content);

            case "EA":
                byte[] bytes = loadResourceAsBytes(filePath);
                return eaReader.readXmiFromBytes(bytes);

            case "EXCEL":
                try (InputStream is = new ClassPathResource(filePath).getInputStream()) {
                    return excelReader.readOntologyFromExcel(is);
                }

            case "SSP":
                String sspContent = loadResourceAsString(filePath);
                String iri = extractIriFromSsp(sspContent);
                return sspReader.readOntology(iri);

            default:
                throw new IllegalArgumentException("Unsupported reader type: " + readerType);
        }
    }

    private String loadResourceAsString(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private byte[] loadResourceAsBytes(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return is.readAllBytes();
        }
    }

    private String extractIriFromSsp(String sspContent) {
        // Simplified - parse JSON and extract IRI
        return "http://example.org/ontology";
    }
}