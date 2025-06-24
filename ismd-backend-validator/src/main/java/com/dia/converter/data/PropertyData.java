package com.dia.converter.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyData {
    private String name;
    private String domain;
    private String description;
    private String definition;
    private String source;

    private String relatedSource;
    private String superProperty;
    private String alternativeName;
    private String equivalentConcept;
    private String identifier;
    private String dataType;

    private String sharedInPPDF;
    private String isPublic;
    private String privacyProvision;

    private String sharingMethod;
    private String acquisitionMethod;
    private String contentType;

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty() &&
                identifier != null && !identifier.trim().isEmpty();
    }
}
