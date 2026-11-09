package com.dia.ofn;

/**
 * Something the conformance gate removed from the converted vocabulary, and why.
 *
 * @param kind       what was removed
 * @param conceptIri IRI of the affected concept, or null for the vocabulary itself
 * @param location   path in the JSON-LD document, e.g. {@code pojmy[řidič]/agenda}
 * @param message    human-readable reason
 */
public record ConformanceIssue(Kind kind, String conceptIri, String location, String message) {

    public enum Kind {
        /** A value with no matching term or shape in the OFN context was dropped. */
        VALUE_DROPPED,
        /** A concept failed the OFN JSON Schema and was left out of the output. */
        CONCEPT_EXCLUDED,
        /** A reference to an excluded concept was removed. */
        REFERENCE_REMOVED,
        /**
         * An input element whose IRI belongs to another vocabulary: it is only referenced, so its own
         * data (name, definition, …) is not part of the output.
         */
        EXTERNAL_CONCEPT,
        /** The vocabulary itself fails the OFN JSON Schema: the conversion is refused (T1). */
        VOCABULARY_INVALID
    }
}
