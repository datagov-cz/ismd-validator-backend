package com.dia.conversion.data;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;
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

    private String sharedInPPDF;
    private String agendaCode;
    private String agendaSystemCode;
    private String isPublic;
    private String privacyProvision;
    private String sharingMethod;
    private String acquisitionMethod;
    private String contentType;

    // Excel only
    private String superRelation;
    private String alternativeName;
    private String equivalentConcept;
    /** Broader concepts (skos:broader) by IRI, for "nadřazený-pojem". */
    private List<String> broaderConcepts = new ArrayList<>();
    private String identifier;
}
