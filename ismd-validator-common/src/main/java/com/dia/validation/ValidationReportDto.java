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
    private Long id;
    private List<ValidationResult> results;
    private boolean isValid;
    private Instant timestamp;
    private Long ontologyId;

    public ValidationReportDto(List<ValidationResult> results,
                               Long ontologyId, Instant timestamp) {
        this.results = results;
        this.ontologyId = ontologyId;
        this.timestamp = timestamp;
    }

    public ValidationReportDto(List<ValidationResult> results, Instant timestamp) {
        this.results = results;
        this.timestamp = timestamp;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public List<ValidationResult> getResults() {
        return results;
    }

    @Override
    public Long getOntologyId() {
        return ontologyId;
    }
}