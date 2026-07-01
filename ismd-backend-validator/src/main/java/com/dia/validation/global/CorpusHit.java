package com.dia.validation.global;

/**
 * A single corpus-comparison hit tied to one uploaded subject.
 *
 * @param uploadedIri the uploaded subject that triggered the hit (focus node).
 * @param otherIri    the conflicting corpus IRI, or {@code null} when the hit is about the
 *                    same IRI (same-IRI and same-IRI-different-name checks).
 * @param value       a human-relevant conflicting value (e.g. the differing/shared label),
 *                    surfaced as the {@code value} of the resulting {@code ValidationResult};
 *                    may be {@code null}.
 */
public record CorpusHit(String uploadedIri, String otherIri, String value) {
}
