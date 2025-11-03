package com.dia.workflow.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Configuration for Excel conversion test cases.
 * Allows flexible configuration of test files, expected outputs, and validation rules.
 */
@Data
@Builder
public class ExcelTestConfiguration {

    /**
     * Unique identifier for this test case
     */
    private String testId;

    /**
     * Human-readable description
     */
    private String description;

    /**
     * Path to input Excel file (relative to test resources)
     */
    private String inputPath;

    /**
     * Path to expected JSON-LD output (relative to test resources)
     */
    private String expectedOutputPath;

    /**
     * Path to JSON-LD context file for validation (relative to test resources)
     */
    private String contextPath;

    /**
     * Expected entity counts (optional)
     */
    private EntityCounts expectedCounts;

    /**
     * List of required characteristics that must be present in output
     */
    private List<String> requiredCharacteristics;

    @Data
    @Builder
    public static class EntityCounts {
        private Integer classes;
        private Integer properties;
        private Integer relationships;
        private Integer hierarchies;
    }

    /**
     * Creates test configuration for the complete test Excel file
     */
    public static ExcelTestConfiguration completeExcel() {
        return ExcelTestConfiguration.builder()
            .testId("complete-excel")
            .description("Complete Excel ontology test with all characteristics")
            .inputPath("com/dia/canonical/complete/testExcelProject.xlsx")
            .expectedOutputPath("com/dia/expected-outputs/complete/excel_output_jsonld.jsonld")
            .contextPath("com/dia/context/json_ld_context.jsonld")
            .expectedCounts(EntityCounts.builder()
                .classes(7)
                .properties(30)
                .relationships(4)
                .hierarchies(3)
                .build())
            .requiredCharacteristics(List.of(
                "typ",
                "název",
                "iri",
                "popis",
                "definice",
                "definiční-obor",
                "obor-hodnot",
                "definující-ustanovení-právního-předpisu",
                "nadřazená-třída",
                "způsob-sdílení-údaje",
                "způsob-získání-údaje",
                "typ-obsahu-údaje",
                "je-sdílen-v-ppdf",
                "ustanovení-dokládající-neveřejnost-údaje",
                "agenda",
                "agendový-informační-systém",
                "ekvivalentní-pojem",
                "alternativní-název",
                "definující-nelegislativní-zdroj",
                "související-nelegislativní-zdroj",
                "nadřazená-vlastnost"
            ))
            .build();
    }

    /**
     * Returns all available Excel test configurations
     */
    public static List<ExcelTestConfiguration> allConfigurations() {
        return List.of(
            completeExcel()
            // Add more test configurations here as needed
        );
    }

    @Override
    public String toString() {
        return testId + " - " + description;
    }
}
