package com.dia.workflow.config;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * Metadata descriptor for test ontologies.
 * Defines what content is expected in a test ontology without hardcoding specific values.
 */
@Data
@Builder
public class TestOntologyMetadata {

    /**
     * Test ontology identifier (e.g., "minimal", "complete", "edge-cases")
     */
    private String testId;

    /**
     * Human-readable description of this test ontology
     */
    private String description;

    /**
     * Expected vocabulary/ontology metadata
     */
    private VocabularyExpectations vocabularyExpectations;

    /**
     * Expected counts for different entity types
     */
    private EntityCounts expectedCounts;

    /**
     * List of OFN characteristics that should be present
     */
    private List<String> requiredCharacteristics;

    @Data
    @Builder
    public static class VocabularyExpectations {
        private Boolean shouldHaveName;
        private Boolean shouldHaveDescription;
        private Boolean shouldHaveNamespace;
        private Boolean shouldHaveDateOfCreation;
        private Boolean shouldHaveDateOfModification;
        private Boolean shouldHaveTemporalData;
    }

    @Data
    @Builder
    public static class EntityCounts {
        private CountExpectation classes;
        private CountExpectation properties;
        private CountExpectation relationships;
        private CountExpectation hierarchies;

        @Data
        @Builder
        public static class CountExpectation {
            private Integer minimum;
            private Integer maximum;
            private Integer exact;

            public boolean matches(int actual) {
                if (exact != null) {
                    return actual == exact;
                }
                boolean meetsMinimum = minimum == null || actual >= minimum;
                boolean meetsMaximum = maximum == null || actual <= maximum;
                return meetsMinimum && meetsMaximum;
            }
        }
    }
}