package com.dia.converter.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelationshipData {
    private String domain;  // First "Subjekt nebo objekt práva" column
    private String name;
    private String range;   // Second "Subjekt nebo objekt práva" column

    private String description;
    private String definition;
    private String source;

    private String relatedSource;
    private String superRelation;
    private String alternativeName;
    private String equivalentConcept;
    private String identifier;

    private String sharedInPPDF;
    private String isPublic;
    private String privacyProvision;

    private String sharingMethod;
    private String acquisitionMethod;
    private String contentType;

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty() &&
                domain != null && !domain.trim().isEmpty() &&
                range != null && !range.trim().isEmpty();
    }
}
