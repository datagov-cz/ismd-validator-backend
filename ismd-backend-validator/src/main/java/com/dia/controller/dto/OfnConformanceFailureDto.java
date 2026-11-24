package com.dia.controller.dto;

import java.util.List;

/**
 * Body of {@code VOCABULARY_OFN_NONCONFORMANT}: what to correct (same shape as in ismd-tool).
 *
 * @param vocabularyIri the vocabulary checked
 * @param issues        every vocabulary-level problem found, none omitted
 */
public record OfnConformanceFailureDto(String vocabularyIri, List<ConformanceIssueDto> issues) {
}
