package com.dia.converter.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassData {
    private String id;
    private String name;
    private String type;
    private String description;
    private String definition;
    private String source;
    private String identifier;

    private String relatedSource;
    private String superClass;
    private String alternativeName;
    private String equivalentConcept;

    private String agendaCode;
    private String agendaSystemCode;

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty();
    }
}