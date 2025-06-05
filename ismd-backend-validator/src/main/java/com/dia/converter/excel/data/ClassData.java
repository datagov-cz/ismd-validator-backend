package com.dia.converter.excel.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassData {
    private String name;
    private String type; // "Subjekt práva" or "Objekt práva"
    private String description;
    private String definition;
    private String source;

    private String relatedSource;
    private String superClass;
    private String alternativeName;
    private String equivalentConcept;
    private String identifier;

    private String agendaCode;
    private String agendaSystemCode;

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty() &&
                identifier != null && !identifier.trim().isEmpty();
    }

    public String getOntologyType() {
        if ("Subjekt práva".equals(type)) {
            return "Typ subjektu práva";
        } else if ("Objekt práva".equals(type)) {
            return "Typ objektu práva";
        }
        return "Třída";
    }
}
