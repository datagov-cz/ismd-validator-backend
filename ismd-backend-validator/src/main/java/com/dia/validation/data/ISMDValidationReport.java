package com.dia.validation.data;

import com.dia.validation.ValidationResult;
import com.dia.validation.ValidationSeverity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public record ISMDValidationReport(List<ValidationResult> results, boolean isValid, Instant timestamp) {

    public static ISMDValidationReport empty() {
        return new ISMDValidationReport(Collections.emptyList(), true, Instant.now());
    }

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

    public List<ValidationResult> getErrors() {
        return results.stream()
                .filter(result -> result.severity() == ValidationSeverity.ERROR)
                .toList();
    }

    public List<ValidationResult> getWarnings() {
        return results.stream()
                .filter(result -> result.severity() == ValidationSeverity.WARNING)
                .toList();
    }

    public List<ValidationResult> getInfoResults() {
        return results.stream()
                .filter(result -> result.severity() == ValidationSeverity.INFO)
                .toList();
    }

    public List<ValidationResult> getResultsBySeverity(ValidationSeverity severity) {
        return results.stream()
                .filter(result -> result.severity() == severity)
                .toList();
    }

    public long getErrorCount() {
        return results.stream()
                .filter(result -> result.severity() == ValidationSeverity.ERROR)
                .count();
    }

    public long getWarningCount() {
        return results.stream()
                .filter(result -> result.severity() == ValidationSeverity.WARNING)
                .count();
    }

    public long getInfoCount() {
        return results.stream()
                .filter(result -> result.severity() == ValidationSeverity.INFO)
                .count();
    }

    public boolean hasErrors() {
        return getErrorCount() > 0;
    }

    public boolean hasWarnings() {
        return getWarningCount() > 0;
    }

    public int getTotalResultCount() {
        return results.size();
    }

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

    public java.util.Map<String, List<ValidationResult>> getResultsByRule() {
        return results.stream()
                .collect(Collectors.groupingBy(ValidationResult::ruleName));
    }

    public java.util.Map<String, List<ValidationResult>> getResultsByFocusNode() {
        return results.stream()
                .filter(result -> result.focusNodeUri() != null)
                .collect(Collectors.groupingBy(ValidationResult::focusNodeUri));
    }

    public List<ValidationResult> getResultsForRule(String ruleName) {
        return results.stream()
                .filter(result -> ruleName.equals(result.ruleName()))
                .toList();
    }

    public List<ValidationResult> getResultsForFocusNode(String focusNodeUri) {
        return results.stream()
                .filter(result -> focusNodeUri.equals(result.focusNodeUri()))
                .toList();
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationReport{valid=%s, results=%d, errors=%d, warnings=%d, information=%d, timestamp=%s}",
                isValid,
                results.size(),
                getErrorCount(),
                getWarningCount(),
                getInfoCount(),
                timestamp
        );
    }
}
