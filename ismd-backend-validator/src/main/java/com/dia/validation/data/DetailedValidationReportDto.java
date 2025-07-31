package com.dia.validation.data;

import java.util.Map;

public record DetailedValidationReportDto(OntologyInfoDto ontology, Map<String, ConceptValidationDto> validation) {
}
