package com.dia.validation.data;

import java.util.Map;

public record ConceptValidationDto(String conceptIri, Map<String, RuleViolationDto> violations) {
}
