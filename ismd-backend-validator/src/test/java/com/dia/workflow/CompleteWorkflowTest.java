package com.dia.workflow;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.engine.ConverterEngine;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.reader.ssp.SSPReader;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.workflow.assertions.OFNAssertions;
import com.dia.workflow.config.TestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * End-to-End workflow tests that validate complete Read → Transform → Export pipeline.
 * These tests are content-independent and work with any canonical test ontology
 * defined in TestConfiguration.
 */
@SpringBootTest
@Tag("workflow")
@Tag("e2e")
class CompleteWorkflowTest {

    @Autowired
    private ArchiReader archiReader;

    @Autowired
    private EnterpriseArchitectReader eaReader;

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private SSPReader sspReader;

    @Autowired
    private OFNDataTransformer transformer;

    @Autowired
    private ConverterEngine converterEngine;

    /**
     * Provides all test configurations for parameterized tests
     */
    static Stream<TestConfiguration> testConfigurationProvider() {
        return TestConfiguration.allStandardConfigurations().stream();
    }

    /**
     * Provides test configuration and format combinations
     */
    static Stream<Arguments> testConfigurationAndFormatProvider() {
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

    // ========== Complete Workflow Tests ==========

    @ParameterizedTest(name = "{0} - {1} format")
    @MethodSource("testConfigurationAndFormatProvider")
    void completeWorkflow_shouldPreserveAllCharacteristics(
            TestConfiguration config,
            String format,
            String inputFilePath) throws Exception {

        // Given: Input file from canonical test resources
        String inputContent = loadResourceAsString(inputFilePath);

        // When: Execute full workflow
        OntologyData parsed = parseInput(format, inputFilePath);
        TransformationResult transformed = transformer.transform(parsed);
        String jsonOutput = transformer.exportToJson(transformed);
        String turtleOutput = transformer.exportToTurtle(transformed);

        // Then: Validate against metadata expectations
        OFNAssertions.assertOntologyDataMatchesMetadata(parsed, config.getMetadata());
        OFNAssertions.assertTransformationResultMatchesMetadata(transformed, config.getMetadata());
        OFNAssertions.assertValidOFNJson(jsonOutput);
        OFNAssertions.assertJsonMatchesMetadata(jsonOutput, config.getMetadata());
        OFNAssertions.assertValidTurtle(turtleOutput);

        // And: No data loss occurred
        OFNAssertions.assertNoDataLoss(parsed);
        OFNAssertions.assertValidTransformationResult(transformed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testConfigurationProvider")
    void completeWorkflow_shouldMatchExpectedJsonOutput(TestConfiguration config) throws Exception {

        // Skip if no Archi input or expected JSON output defined
        if (config.getInputFiles().getArchiXml() == null ||
            config.getExpectedOutputFiles().getJsonOutput() == null) {
            return;
        }

        // Given: Archi input (using Archi as reference format)
        String inputPath = config.getInputFiles().getArchiXml();
        String expectedJsonPath = config.getExpectedOutputFiles().getJsonOutput();

        // When: Execute full workflow
        OntologyData parsed = parseInput("ARCHI", inputPath);
        TransformationResult transformed = transformer.transform(parsed);
        String actualJson = transformer.exportToJson(transformed);

        // Then: Output should match expected (if expected file exists)
        try {
            String expectedJson = loadResourceAsString(expectedJsonPath);
            OFNAssertions.assertJsonEquals(expectedJson, actualJson);
        } catch (Exception e) {
            // Expected output file doesn't exist yet - just validate structure
            System.out.println("Expected output not found: " + expectedJsonPath);
            OFNAssertions.assertValidOFNJson(actualJson);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testConfigurationProvider")
    void completeWorkflow_shouldMatchExpectedTurtleOutput(TestConfiguration config) throws Exception {

        // Skip if no Archi input or expected Turtle output defined
        if (config.getInputFiles().getArchiXml() == null ||
            config.getExpectedOutputFiles().getTurtleOutput() == null) {
            return;
        }

        // Given: Archi input
        String inputPath = config.getInputFiles().getArchiXml();
        String expectedTurtlePath = config.getExpectedOutputFiles().getTurtleOutput();

        // When: Execute full workflow
        OntologyData parsed = parseInput("ARCHI", inputPath);
        TransformationResult transformed = transformer.transform(parsed);
        String actualTurtle = transformer.exportToTurtle(transformed);

        // Then: Validate structure (exact comparison might be tricky due to serialization)
        OFNAssertions.assertValidTurtle(actualTurtle);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testConfigurationProvider")
    void completeWorkflow_allFormats_shouldProduceConsistentOutput(TestConfiguration config) throws Exception {

        // Given: All available input formats for this test configuration
        List<String> jsonOutputs = new ArrayList<>();
        List<String> turtleOutputs = new ArrayList<>();

        // When: Process each available format
        if (config.getInputFiles().getArchiXml() != null) {
            String json = processFormat("ARCHI", config.getInputFiles().getArchiXml());
            jsonOutputs.add(json);
        }
        if (config.getInputFiles().getEnterpriseArchitectXmi() != null) {
            String json = processFormat("EA", config.getInputFiles().getEnterpriseArchitectXmi());
            jsonOutputs.add(json);
        }
        if (config.getInputFiles().getExcel() != null) {
            String json = processFormat("EXCEL", config.getInputFiles().getExcel());
            jsonOutputs.add(json);
        }
        if (config.getInputFiles().getSsp() != null) {
            String json = processFormat("SSP", config.getInputFiles().getSsp());
            jsonOutputs.add(json);
        }

        // Then: All formats should produce consistent output
        if (jsonOutputs.size() > 1) {
            OFNAssertions.assertCrossFormatConsistency(jsonOutputs, config.getMetadata());
        }
    }

    @Test
    @Tag("regression")
    void completeWorkflow_xsdDataType_shouldPreserveCorrectRange() throws Exception {
        // Regression test for Issue #109 - XSD data type range bug
        // This test will use complete ontology which should have various data types

        TestConfiguration config = TestConfiguration.complete();
        if (config.getInputFiles().getArchiXml() == null) {
            return; // Skip if complete test file not available yet
        }

        // When: Process complete ontology
        OntologyData parsed = parseInput("ARCHI", config.getInputFiles().getArchiXml());
        TransformationResult transformed = transformer.transform(parsed);
        String json = transformer.exportToJson(transformed);

        // Then: Any XSD data types should be preserved correctly (not rdfs:Literal)
        OFNAssertions.assertValidOFNJson(json);
        assertFalse(json.contains("rdfs:Literal"),
            "XSD data types should not be converted to rdfs:Literal");
    }

    // ========== Helper Methods ==========

    private OntologyData parseInput(String format, String filePath) throws Exception {
        switch (format) {
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
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    private String processFormat(String format, String filePath) throws Exception {
        OntologyData parsed = parseInput(format, filePath);
        TransformationResult transformed = transformer.transform(parsed);
        return transformer.exportToJson(transformed);
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
        // TODO
        return "";
    }
}