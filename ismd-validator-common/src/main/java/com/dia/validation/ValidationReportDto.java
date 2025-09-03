package com.dia.validation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ValidationReportDto implements ValidationReport {
    private List<ValidationResult> results;
    private boolean isValid;
    private Instant timestamp;
    private String ontologyIri;

    public ValidationReportDto(List<ValidationResult> results,
                               String ontologyIri, Instant timestamp) {
        this.results = results;
        this.ontologyIri = ontologyIri;
        this.timestamp = timestamp;
    }

    @Override
    public List<ValidationResult> getResults() {
        return results;
    }

    @Override
    public List<ValidationResult> getErrors() {
        return results.stream()
                .filter(r -> r.severity() == ValidationSeverity.ERROR)
                .toList();
    }

    @Override
    public List<ValidationResult> getWarnings() {
        return results.stream()
                .filter(r -> r.severity() == ValidationSeverity.WARNING)
                .toList();
    }

    @Override
    public long getErrorCount() {
        return results.stream()
                .filter(r -> r.severity() == ValidationSeverity.ERROR)
                .count();
    }

    @Override
    public long getWarningCount() {
        return results.stream()
                .filter(r -> r.severity() == ValidationSeverity.WARNING)
                .count();
    }

    @Override
    public boolean hasErrors() {
        return getErrorCount() > 0;
    }

    @Override
    public String getSummary() {
        if (results.isEmpty()) {
            return "No validation issues found";
        }
        return String.format(
                "Validation completed with %d errors, %d warnings",
                getErrorCount(), getWarningCount()
        );
    }
}