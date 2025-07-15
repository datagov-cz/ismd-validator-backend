package com.dia.validation.data;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ValidationResult {

    /**
     * Severity level of the validation result
     */
    private final ValidationSeverity severity;

    /**
     * Human-readable validation message
     */
    private final String message;

    /**
     * Name/identifier of the rule that generated this result
     */
    private final String ruleName;

    /**
     * URI of the focus node (the resource being validated)
     */
    private final String focusNodeUri;

    /**
     * URI of the result path (the property that failed validation)
     */
    private final String resultPathUri;

    /**
     * String representation of the value that failed validation
     */
    private final String value;

    /**
     * Check if this result represents an error
     */
    public boolean isError() {
        return severity == ValidationSeverity.ERROR;
    }

    /**
     * Check if this result represents a warning
     */
    public boolean isWarning() {
        return severity == ValidationSeverity.WARNING;
    }

    /**
     * Check if this result represents an info message
     */
    public boolean isInfo() {
        return severity == ValidationSeverity.INFO;
    }

    /**
     * Get a short description of the focus node (last part of URI)
     */
    public String getFocusNodeName() {
        if (focusNodeUri == null) {
            return null;
        }

        int lastSlash = focusNodeUri.lastIndexOf('/');
        int lastHash = focusNodeUri.lastIndexOf('#');
        int splitIndex = Math.max(lastSlash, lastHash);

        if (splitIndex >= 0 && splitIndex < focusNodeUri.length() - 1) {
            return focusNodeUri.substring(splitIndex + 1);
        }

        return focusNodeUri;
    }

    /**
     * Get a short description of the result path (last part of URI)
     */
    public String getResultPathName() {
        if (resultPathUri == null) {
            return null;
        }

        int lastSlash = resultPathUri.lastIndexOf('/');
        int lastHash = resultPathUri.lastIndexOf('#');
        int splitIndex = Math.max(lastSlash, lastHash);

        if (splitIndex >= 0 && splitIndex < resultPathUri.length() - 1) {
            return resultPathUri.substring(splitIndex + 1);
        }

        return resultPathUri;
    }

    /**
     * Get a formatted description of this validation result
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append(severity.getDisplayName().toUpperCase()).append(": ");
        sb.append(message);

        if (focusNodeUri != null) {
            sb.append(" (Focus: ").append(getFocusNodeName()).append(")");
        }

        if (resultPathUri != null) {
            sb.append(" (Property: ").append(getResultPathName()).append(")");
        }

        if (value != null && !value.trim().isEmpty()) {
            sb.append(" (Value: ").append(value).append(")");
        }

        return sb.toString();
    }

    /**
     * Create a compact string representation
     */
    public String toCompactString() {
        return String.format("[%s] %s - %s",
                severity.getDisplayName(),
                ruleName,
                message);
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationResult{severity=%s, rule='%s', message='%s', focusNode='%s'}",
                severity,
                ruleName,
                message,
                getFocusNodeName()
        );
    }
}
