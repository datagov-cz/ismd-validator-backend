package com.dia.workflow.config;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a complete test case including all input formats and expected outputs.
 */
@Data
@Builder
public class TestConfiguration {

    /**
     * Test case identifier (e.g., "minimal", "complete")
     */
    private String testCaseId;

    /**
     * Description of this test case
     */
    private String description;

    /**
     * File paths for input files in different formats
     */
    private InputFiles inputFiles;

    /**
     * File paths for expected output files
     */
    private ExpectedOutputFiles expectedOutputFiles;

    /**
     * Metadata describing the expected content
     */
    private TestOntologyMetadata metadata;

    @Data
    @Builder
    public static class InputFiles {
        private String archiXml;
        private String enterpriseArchitectXmi;
        private String excel;
        private String ssp;

        /**
         * Returns all available input files as a map
         */
        public Map<String, String> asMap() {
            return Map.of(
                "ARCHI", archiXml != null ? archiXml : "",
                "EA", enterpriseArchitectXmi != null ? enterpriseArchitectXmi : "",
                "EXCEL", excel != null ? excel : "",
                "SSP", ssp != null ? ssp : ""
            );
        }
    }

    @Data
    @Builder
    public static class ExpectedOutputFiles {
        private String jsonOutput;
        private String turtleOutput;
    }

    /**
     * Creates a minimal test configuration
     */
    public static TestConfiguration minimal() {
        return TestConfiguration.builder()
            .testCaseId("minimal")
            .description("Minimal valid ontology with bare minimum required fields")
            .inputFiles(InputFiles.builder()
                .archiXml("com/dia/canonical/minimal/minimal-archi.xml")
                .enterpriseArchitectXmi("com/dia/canonical/minimal/minimal-ea.xml")
                .excel("com/dia/canonical/minimal/minimal-excel.xlsx")
                .ssp("com/dia/canonical/minimal/minimal-ssp.json")
                .build())
            .expectedOutputFiles(ExpectedOutputFiles.builder()
                .jsonOutput("com/dia/expected-outputs/minimal/minimal.json")
                .turtleOutput("com/dia/expected-outputs/minimal/minimal.ttl")
                .build())
            .metadata(TestOntologyMetadata.builder()
                .testId("minimal")
                .description("Minimal ontology")
                .vocabularyExpectations(TestOntologyMetadata.VocabularyExpectations.builder()
                    .shouldHaveName(true)
                    .shouldHaveDescription(false)
                    .shouldHaveNamespace(false)
                    .shouldHaveDateOfCreation(false)
                    .shouldHaveDateOfModification(false)
                    .shouldHaveTemporalData(false)
                    .build())
                .expectedCounts(TestOntologyMetadata.EntityCounts.builder()
                    .classes(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(1)
                        .build())
                    .properties(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(1)
                        .build())
                    .relationships(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(0)
                        .build())
                    .hierarchies(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(0)
                        .build())
                    .build())
                .requiredCharacteristics(List.of(
                    "typ",
                    "název",
                    "iri"
                ))
                .build())
            .build();
    }

    /**
     * Creates a complete test configuration
     */
    public static TestConfiguration complete() {
        return TestConfiguration.builder()
            .testCaseId("complete")
            .description("Complete ontology with all OFN characteristics populated")
            .inputFiles(InputFiles.builder()
                .archiXml("com/dia/canonical/complete/complete-archi.xml")
                .enterpriseArchitectXmi("com/dia/canonical/complete/complete-ea.xml")
                .excel("com/dia/canonical/complete/complete-excel.xlsx")
                .ssp("com/dia/canonical/complete/complete-ssp.json")
                .build())
            .expectedOutputFiles(ExpectedOutputFiles.builder()
                .jsonOutput("com/dia/expected-outputs/complete/complete.json")
                .turtleOutput("com/dia/expected-outputs/complete/complete.ttl")
                .build())
            .metadata(TestOntologyMetadata.builder()
                .testId("complete")
                .description("Complete ontology with all OFN characteristics")
                .vocabularyExpectations(TestOntologyMetadata.VocabularyExpectations.builder()
                    .shouldHaveName(true)
                    .shouldHaveDescription(true)
                    .shouldHaveNamespace(true)
                    .shouldHaveDateOfCreation(true)
                    .shouldHaveDateOfModification(true)
                    .shouldHaveTemporalData(true)
                    .build())
                .expectedCounts(TestOntologyMetadata.EntityCounts.builder()
                    .classes(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(2)
                        .build())
                    .properties(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(2)
                        .build())
                    .relationships(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(1)
                        .build())
                    .hierarchies(TestOntologyMetadata.EntityCounts.CountExpectation.builder()
                        .minimum(0)
                        .build())
                    .build())
                .requiredCharacteristics(List.of(
                    "typ",
                    "název",
                    "iri",
                    "popis",
                    "definice",
                    "zdroj",
                    "je-pojem-veřejný",
                    "způsob-sdílení",
                    "typ-obsahu",
                    "definiční-obor",
                    "obor-hodnot"
                        // TODO add more
                ))
                .build())
            .build();
    }

    /**
     * Returns all standard test configurations
     */
    public static List<TestConfiguration> allStandardConfigurations() {
        return List.of(
            minimal(),
            complete()
        );
    }
}