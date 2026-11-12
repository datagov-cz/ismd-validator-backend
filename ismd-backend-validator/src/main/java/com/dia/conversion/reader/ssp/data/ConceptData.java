package com.dia.conversion.reader.ssp.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConceptData {
    private String iri;
    private String name;
    private String definition;
    private String source;
    private String note;
    private List<String> broader = new ArrayList<>();
    private List<String> alternativeNames = new ArrayList<>();

    public void addBroader(String iri) {
        if (iri != null && !broader.contains(iri)) {
            broader.add(iri);
        }
    }

    public void addAlternativeName(String altName) {
        if (altName != null && !altName.trim().isEmpty()) {
            this.alternativeNames.add(altName.trim());
        }
    }
}
