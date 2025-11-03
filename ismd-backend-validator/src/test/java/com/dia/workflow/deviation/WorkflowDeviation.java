package com.dia.workflow.deviation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a deviation between expected and actual workflow output.
 * Used to isolate and report where the conversion process deviated from expectations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDeviation {

    /**
     * Type of deviation
     */
    private DeviationType type;

    /**
     * Location where deviation occurred (e.g., "pojmy[3].název")
     */
    private String location;

    /**
     * Human-readable message describing the deviation
     */
    private String message;

    /**
     * Expected value (if applicable)
     */
    private Object expectedValue;

    /**
     * Actual value (if applicable)
     */
    private Object actualValue;

    /**
     * Severity level
     */
    private Severity severity;

    public enum DeviationType {
        MISSING_FIELD("Missing Field"),
        EXTRA_FIELD("Extra Field"),
        VALUE_MISMATCH("Value Mismatch"),
        TYPE_MISMATCH("Type Mismatch"),
        COUNT_MISMATCH("Count Mismatch"),
        STRUCTURE_MISMATCH("Structure Mismatch"),
        CONTEXT_VIOLATION("Context Violation");

        private final String displayName;

        DeviationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Severity {
        CRITICAL,  // Must be fixed - breaks functionality
        HIGH,      // Should be fixed - incorrect output
        MEDIUM,    // Should be investigated - potential issue
        LOW        // Nice to fix - cosmetic or minor
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append(type.getDisplayName()).append(" at ").append(location);
        sb.append("\n  ").append(message);

        if (expectedValue != null || actualValue != null) {
            sb.append("\n  Expected: ").append(formatValue(expectedValue));
            sb.append("\n  Actual:   ").append(formatValue(actualValue));
        }

        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        String str = value.toString();
        if (str.length() > 100) {
            return str.substring(0, 97) + "...";
        }
        return str;
    }
}
