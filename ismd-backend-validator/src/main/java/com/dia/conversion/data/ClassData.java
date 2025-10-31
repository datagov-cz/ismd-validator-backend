package com.dia.conversion.data;

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

    private String sharedInPPDF;
    private String agendaCode;
    private String agendaSystemCode;
    private String isPublic;
    private String privacyProvision;
    private String sharingMethod;
    private String acquisitionMethod;
    private String contentType;

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty();
    }
}