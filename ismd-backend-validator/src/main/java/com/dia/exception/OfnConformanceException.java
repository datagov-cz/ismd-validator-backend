package com.dia.exception;

import com.dia.ofn.ConformanceIssue;
import lombok.Getter;

import java.util.List;

/**
 * The vocabulary itself does not conform to OFN slovníky (tier T1, as in ismd-tool): the whole
 * conversion is refused with every vocabulary-level issue, instead of returning a non-conforming document.
 */
@Getter
public class OfnConformanceException extends ConversionException {

    private final String vocabularyIri;
    private final List<ConformanceIssue> issues;

    public OfnConformanceException(String vocabularyIri, List<ConformanceIssue> issues) {
        super("Slovník nesplňuje standard OFN Slovníky.");
        this.vocabularyIri = vocabularyIri;
        this.issues = List.copyOf(issues);
    }
}
