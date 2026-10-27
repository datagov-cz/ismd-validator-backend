package com.dia.conversion.data;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;
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
    /** Broader concepts (skos:broader) by IRI, for "nadřazený-pojem". */
    private List<String> broaderConcepts = new ArrayList<>();

    private String sharedInPPDF;
    private String agendaCode;
    private String agendaSystemCode;
    private String isPublic;
    private String privacyProvision;
    private String sharingMethod;
    private String acquisitionMethod;
    private String contentType;
    /** IRI of the codelist whose items are the instances of this class. */
    private String codelistIri;
    /** IRI of the codelist's umbrella dataset in NKOD. */
    private String codelistNkodDataset;

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty();
    }
}