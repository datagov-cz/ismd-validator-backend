package com.dia.workflow.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;

import static com.dia.constants.ExportConstants.Json.IRI;
import static com.dia.constants.VocabularyConstants.*;

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
                TYP,
                NAZEV,
                IRI,
                POPIS,
                DEFINICE,
                DEFINICNI_OBOR,
                OBOR_HODNOT,
                DEFINUJICI_USTANOVENI,
                NADRAZENA_TRIDA,
                ZPUSOB_SDILENI_ALT,
                ZPUSOB_ZISKANI_ALT,
                TYP_OBSAHU_ALT,
                JE_PPDF,
                USTANOVENI_NEVEREJNOST,
                AGENDA,
                AIS,
                EKVIVALENTNI_POJEM,
                ALTERNATIVNI_NAZEV,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ,
                    NADRAZENA_VLASTNOST
            ))
            .build();
    }

    /**
     * Creates test configuration for the complete test EA file
     * Note: EA conversion currently does not support:
     * - JE_PPDF (je-sdílen-v-ppdf) - extraction logic exists but not working correctly
     * - NADRAZENA_VLASTNOST (nadřazená-vlastnost) - extraction logic not implemented
     * These characteristics are present in the EA XML but not extracted by EnterpriseArchitectReader
     */
    public static ExcelTestConfiguration completeEA() {
        return ExcelTestConfiguration.builder()
            .testId("complete-ea")
            .description("Complete EA (Enterprise Architect) ontology test with JSON output")
            .inputPath("com/dia/canonical/complete/testEAInput.xml")
            .expectedOutputPath("com/dia/expected-outputs/complete/ea_output_jsonld.jsonld")
            .contextPath("com/dia/context/json_ld_context.jsonld")
            .expectedCounts(EntityCounts.builder()
                .classes(7)
                .properties(30)
                .relationships(4)
                .hierarchies(2)
                .build())
            .requiredCharacteristics(List.of(
                TYP,
                NAZEV,
                IRI,
                POPIS,
                DEFINICE,
                DEFINICNI_OBOR,
                OBOR_HODNOT,
                DEFINUJICI_USTANOVENI,
                NADRAZENA_TRIDA,
                ZPUSOB_SDILENI_ALT,
                ZPUSOB_ZISKANI_ALT,
                TYP_OBSAHU_ALT,
                // JE_PPDF - not currently extracted from EA XML
                USTANOVENI_NEVEREJNOST,
                AGENDA,
                AIS,
                EKVIVALENTNI_POJEM,
                ALTERNATIVNI_NAZEV,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ
                // NADRAZENA_VLASTNOST - not currently extracted from EA XML
            ))
            .build();
    }

    /**
     * Returns all available Excel test configurations
     */
    public static List<ExcelTestConfiguration> allConfigurations() {
        return List.of(
            completeExcel(),
            completeEA()
        );
    }

    @Override
    public String toString() {
        return testId + " - " + description;
    }
}
