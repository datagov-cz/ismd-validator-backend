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
}