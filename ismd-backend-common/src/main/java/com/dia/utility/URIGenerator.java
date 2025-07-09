package com.dia.utility;

import lombok.Getter;
import lombok.Setter;

import static com.dia.constants.ArchiConstants.DEFAULT_NS;

/**
 * URI Generator helper class
 */
@Setter
@Getter
public class URIGenerator {
    private String effectiveNamespace = DEFAULT_NS;

    private String vocabularyName;

    public String generateConceptURI(String conceptName, String identifier) {
        if (identifier != null && !identifier.trim().isEmpty()) {
            String trimmedIdentifier = identifier.trim();

            if (UtilityMethods.isValidIRI(trimmedIdentifier)) {
                return trimmedIdentifier;
            }

            String sanitizedIdentifier = UtilityMethods.sanitizeForIRI(trimmedIdentifier);
            return buildConceptURI(sanitizedIdentifier);
        }

        String sanitizedName = UtilityMethods.sanitizeForIRI(conceptName);
        return buildConceptURI(sanitizedName);
    }

    private String buildConceptURI(String sanitizedConceptName) {
        StringBuilder uriBuilder = new StringBuilder(effectiveNamespace);

        if (uriBuilder.toString().endsWith("/")) {
            uriBuilder.setLength(uriBuilder.length() - 1);
        }

        if (vocabularyName != null && !vocabularyName.trim().isEmpty()) {
            String sanitizedVocabName = UtilityMethods.sanitizeForIRI(vocabularyName);
            uriBuilder.append("/").append(sanitizedVocabName);
        }

        uriBuilder.append("/pojem/").append(sanitizedConceptName);

        return uriBuilder.toString();
    }

    public String generateVocabularyURI(String vocabularyName, String identifier) {
        if (identifier != null && !identifier.trim().isEmpty()) {
            String trimmedIdentifier = identifier.trim();

            if (UtilityMethods.isValidIRI(trimmedIdentifier)) {
                return trimmedIdentifier;
            }

            String sanitizedIdentifier = UtilityMethods.sanitizeForIRI(trimmedIdentifier);
            return buildVocabularyURI(sanitizedIdentifier);
        }

        if (vocabularyName == null || vocabularyName.trim().isEmpty()) {
            return effectiveNamespace;
        }

        String sanitizedName = UtilityMethods.sanitizeForIRI(vocabularyName);
        return buildVocabularyURI(sanitizedName);
    }

    private String buildVocabularyURI(String sanitizedVocabularyName) {
        String baseNamespace = effectiveNamespace;
        if (baseNamespace.endsWith("/")) {
            baseNamespace = baseNamespace.substring(0, baseNamespace.length() - 1);
        }
        return baseNamespace + "/" + sanitizedVocabularyName;
    }
}