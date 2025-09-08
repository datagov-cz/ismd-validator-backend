package com.dia.validation;

import java.time.Instant;
import java.util.List;

public interface ValidationReport {
    Long getId();
    List<ValidationResult> getResults();
    boolean isValid();
    Instant getTimestamp();
    Long getOntologyId();
}