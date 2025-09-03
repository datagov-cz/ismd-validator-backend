package com.dia.validation;

import java.time.Instant;
import java.util.List;

public interface ValidationReport {
    List<ValidationResult> getResults();
    boolean isValid();
    Instant getTimestamp();
    String getOntologyIri();
}