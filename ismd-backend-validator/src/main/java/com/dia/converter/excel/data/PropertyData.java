package com.dia.converter.excel.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyData {
    private String name;
    private String domain; // "Subjekt nebo objekt práva" column
    private String description;
    private String definition;
    private String source;

    private String relatedSource;
    private String superProperty;
    private String alternativeName;
    private String equivalentConcept;
    private String identifier;
    private String dataType;

    private String sharedInPPDF; // "Je pojem sdílen v PPDF?"
    private String isPublic;     // "Je pojem veřejný?"
    private String privacyProvision; // "Ustanovení dokládající neveřejnost pojmu"

    private String sharingMethod;     // "Způsob sdílení údaje"
    private String acquisitionMethod; // "Způsob získání údaje"
    private String contentType; // "Typ obsahu údaje"

    public boolean hasValidData() {
        return name != null && !name.trim().isEmpty() &&
                identifier != null && !identifier.trim().isEmpty();
    }

    public boolean isPublicProperty() {
        return !"Ne".equals(isPublic) && privacyProvision == null || privacyProvision.trim().isEmpty();
    }
}
