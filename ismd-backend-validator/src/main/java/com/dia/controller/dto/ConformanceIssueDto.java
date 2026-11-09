package com.dia.controller.dto;

import com.dia.ofn.ConformanceIssue;

import java.util.List;

/**
 * Something the conversion left out because it does not conform to OFN slovníky.
 *
 * @param kind       VALUE_DROPPED, CONCEPT_EXCLUDED, REFERENCE_REMOVED or EXTERNAL_CONCEPT; VOCABULARY_INVALID
 *                   only in the body of a refused conversion ({@link OfnConformanceFailureDto})
 * @param conceptIri the affected concept, or null for the vocabulary
 * @param location   where in the OFN document, e.g. {@code pojmy[řidič]/agenda}
 * @param message    reason, in Czech
 */
public record ConformanceIssueDto(String kind, String conceptIri, String location, String message) {

    public static List<ConformanceIssueDto> from(List<ConformanceIssue> issues) {
        return issues.stream()
                .map(issue -> new ConformanceIssueDto(issue.kind().name(), issue.conceptIri(),
                        issue.location(), issue.message()))
                .toList();
    }
}
