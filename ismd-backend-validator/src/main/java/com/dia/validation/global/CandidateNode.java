package com.dia.validation.global;

import java.util.List;

/**
 * An uploaded subject (concept or vocabulary) that the corpus uniqueness checks compare
 * against the published corpus.
 *
 * @param iri    the subject IRI as it appears in the uploaded model.
 * @param labels its labels, each carrying a language tag ({@code ""} for untagged literals).
 */
public record CandidateNode(String iri, List<Label> labels) {

    /**
     * A single label with its language tag. {@code lang} is {@code ""} for an untagged
     * literal; untagged labels only ever compare against other untagged labels
     * (lang-scoped comparison), which is what kills the multilingual false-positive.
     */
    public record Label(String lang, String text) {
    }
}
