package com.dia.converter.reader.ssp.data;

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
    private String broader;
    private List<String> alternativeNames = new ArrayList<>();

    public void addAlternativeName(String altName) {
        if (altName != null && !altName.trim().isEmpty()) {
            this.alternativeNames.add(altName.trim());
        }
    }
}
