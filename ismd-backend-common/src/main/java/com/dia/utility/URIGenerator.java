package com.dia.utility;

import lombok.Getter;
import lombok.Setter;

import static com.dia.constants.ArchiOntologyConstants.NS;

/**
 * URI Generator helper class
 */
@Setter
@Getter
public class URIGenerator {
    private String effectiveNamespace = NS;

    public String generateClassURI(String className, String identifier) {
        if (identifier != null && !identifier.trim().isEmpty() && UtilityMethods.isValidUrl(identifier)) {
            return identifier;
        }

        String sanitizedName = UtilityMethods.sanitizeForIRI(className);
        return effectiveNamespace + sanitizedName;
    }

    public String generatePropertyURI(String propertyName, String identifier) {
        return generateClassURI(propertyName, identifier);
    }

    public String generateRelationshipURI(String relationshipName, String identifier) {
        return generateClassURI(relationshipName, identifier);
    }
}
