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
public class WorkflowTestConfiguration {

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
    public static WorkflowTestConfiguration completeExcel() {
        return WorkflowTestConfiguration.builder()
            .testId("complete-excel")
            .description("Complete Excel ontology test with all characteristics")
            .inputPath("com/dia/canonical/complete/testExcelProject.xlsx")
            .expectedOutputPath("com/dia/expected-outputs/complete/excel_output_jsonld.jsonld")
            .contextPath("com/dia/context/json_ld_context.jsonld")
            .expectedCounts(EntityCounts.builder()
                .classes(6)  // 6 local classes - Adresa is external reference and should not be counted
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
     * Creates test configuration for the complete test Archi file.
     * Note: Archi and Excel produce identical outputs, so they share the same expected output file.
     */
    public static WorkflowTestConfiguration completeArchi() {
        return WorkflowTestConfiguration.builder()
            .testId("complete-archi")
            .description("Complete Archi XML ontology test - unified with Excel output")
            .inputPath("com/dia/canonical/complete/testArchiInput.xml")
            .expectedOutputPath("com/dia/expected-outputs/complete/excel_output_jsonld.jsonld")
            .contextPath("com/dia/context/json_ld_context.jsonld")
            .expectedCounts(EntityCounts.builder()
                .classes(6)  // 6 local classes - Adresa is external reference and should not be counted
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
     * Creates test configuration for the complete test EA (Enterprise Architect) file
     */
    public static WorkflowTestConfiguration completeEA() {
        return WorkflowTestConfiguration.builder()
            .testId("complete-ea")
            .description("Complete Enterprise Architect ontology test with all characteristics")
            .inputPath("com/dia/canonical/complete/testEAInput.xml")
            .expectedOutputPath("com/dia/expected-outputs/complete/ea_output_jsonld.jsonld")
            .contextPath("com/dia/context/json_ld_context.jsonld")
            .expectedCounts(EntityCounts.builder()
                .classes(6)
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
     * Returns all available workflow test configurations for Excel and Archi.
     */
    public static List<WorkflowTestConfiguration> allConfigurations() {
        return List.of(
            completeExcel(),
            completeArchi()
        );
    }

    /**
     * Returns all EA (Enterprise Architect) workflow test configurations.
     */
    public static List<WorkflowTestConfiguration> eaConfigurations() {
        return List.of(
            completeEA()
        );
    }

    @Override
    public String toString() {
        return testId + " - " + description;
    }
}
