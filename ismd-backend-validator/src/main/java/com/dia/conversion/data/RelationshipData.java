package com.dia.conversion.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelationshipData {
    private String domain;
    private String name;
    private String range;
    private String relationshipType;

    private String description;
    private String definition;
    private String source;

    private String relatedSource;
    // Excel only
    private String superRelation;
    private String alternativeName;
    private String equivalentConcept;
    private String identifier;
}
