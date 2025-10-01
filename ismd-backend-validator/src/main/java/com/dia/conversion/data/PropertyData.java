package com.dia.conversion.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyData {
    private String id;
    private String name;
    private String domain;
    private String description;
    private String definition;
    private String source;
    private String identifier;

    private String relatedSource;
    private String superProperty;
    private String alternativeName;
    private String equivalentConcept;
    private String dataType;
    private String sharedInPPDF;
}
