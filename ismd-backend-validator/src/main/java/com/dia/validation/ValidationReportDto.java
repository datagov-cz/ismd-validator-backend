package com.dia.validation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body of the {@code /validate} endpoints. Its JSON shape ({@code id, results, timestamp,
 * ontologyIri}) is the API contract with consumers such as the tool backend, which keeps its own copy
 * of this type — change it only together with {@code ValidationReportContractTest}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ValidationReportDto {
    private List<ValidationResult> results;
    private Instant timestamp;
    private String ontologyIri;

    public ValidationReportDto(List<ValidationResult> results,
                               String ontologyIri, Instant timestamp) {
        this.results = results;
        this.ontologyIri = ontologyIri;
        this.timestamp = timestamp;
    }

    public ValidationReportDto(List<ValidationResult> results, Instant timestamp) {
        this.results = results;
        this.timestamp = timestamp;
    }

    /** Serialized as {@code id}; kept so the response shape stays unchanged for existing consumers. */
    public Long getId() {
        return UUID.randomUUID().getMostSignificantBits();
    }
}