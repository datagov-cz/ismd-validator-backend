package com.dia.validation.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class ISMDValidationReport {

    private final List<ValidationResult> results;
    private final boolean isValid;
    private final Instant timestamp;

    /**
     * Create an empty validation report (all rules passed)
     */
    public static ISMDValidationReport empty() {
        return new ISMDValidationReport(Collections.emptyList(), true, Instant.now());
    }

    /**
     * Create an error validation report
     */
    public static ISMDValidationReport error(String errorMessage) {
        ValidationResult errorResult = new ValidationResult(
                ValidationSeverity.ERROR,
                errorMessage,
                "system-error",
                null,
                null,
                null
        );
        return new ISMDValidationReport(
                Collections.singletonList(errorResult),
                false,
                Instant.now()
        );
    }

    /**
     * Get all error results
     */
    public List<ValidationResult> getErrors() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.ERROR)
                .toList();
    }

    /**
     * Get all warning results
     */
    public List<ValidationResult> getWarnings() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.WARNING)
                .toList();
    }

    /**
     * Get all info results
     */
    public List<ValidationResult> getInfoResults() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.INFO)
                .toList();
    }

    /**
     * Get results of specific severity
     */
    public List<ValidationResult> getResultsBySeverity(ValidationSeverity severity) {
        return results.stream()
                .filter(result -> result.getSeverity() == severity)
                .toList();
    }

    /**
     * Get all errors and warnings (issues that need attention)
     */
    public List<ValidationResult> getErrorsAndWarnings() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.ERROR ||
                        result.getSeverity() == ValidationSeverity.WARNING)
                .toList();
    }

    /**
     * Get count of error results
     */
    public long getErrorCount() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.ERROR)
                .count();
    }

    /**
     * Get count of warning results
     */
    public long getWarningCount() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.WARNING)
                .count();
    }

    /**
     * Get count of info results
     */
    public long getInfoCount() {
        return results.stream()
                .filter(result -> result.getSeverity() == ValidationSeverity.INFO)
                .count();
    }

    /**
     * Check if validation has any errors
     */
    public boolean hasErrors() {
        return getErrorCount() > 0;
    }

    /**
     * Check if validation has any warnings
     */
    public boolean hasWarnings() {
        return getWarningCount() > 0;
    }

    /**
     * Check if validation has any issues (errors or warnings)
     */
    public boolean hasIssues() {
        return hasErrors() || hasWarnings();
    }

    /**
     * Get total number of validation results
     */
    public int getTotalResultCount() {
        return results.size();
    }

    /**
     * Get summary string of validation results
     */
    public String getSummary() {
        if (results.isEmpty()) {
            return "No validation issues found";
        }

        return String.format(
                "Validation completed with %d errors, %d warnings, %d info messages",
                getErrorCount(),
                getWarningCount(),
                getInfoCount()
        );
    }

    /**
     * Group results by rule name
     */
    public java.util.Map<String, List<ValidationResult>> getResultsByRule() {
        return results.stream()
                .collect(Collectors.groupingBy(ValidationResult::getRuleName));
    }

    /**
     * Group results by focus node
     */
    public java.util.Map<String, List<ValidationResult>> getResultsByFocusNode() {
        return results.stream()
                .filter(result -> result.getFocusNodeUri() != null)
                .collect(Collectors.groupingBy(ValidationResult::getFocusNodeUri));
    }

    /**
     * Get results for a specific rule
     */
    public List<ValidationResult> getResultsForRule(String ruleName) {
        return results.stream()
                .filter(result -> ruleName.equals(result.getRuleName()))
                .toList();
    }

    /**
     * Get results for a specific focus node
     */
    public List<ValidationResult> getResultsForFocusNode(String focusNodeUri) {
        return results.stream()
                .filter(result -> focusNodeUri.equals(result.getFocusNodeUri()))
                .toList();
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationReport{valid=%s, results=%d, errors=%d, warnings=%d, timestamp=%s}",
                isValid,
                results.size(),
                getErrorCount(),
                getWarningCount(),
                timestamp
        );
    }
}
