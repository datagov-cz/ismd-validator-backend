package com.dia.constants;

import java.util.Map;

public final class TypeMappings {

    /**
     * Mapping from Archimate element types to OFN ontology types
     */
    public static final Map<String, String> ARCHIMATE_TO_OFN = Map.of(
            "typ subjektu", "Subjekt práva",
            "typ objektu", "Objekt práva",
            "typ vlastnosti", "Vlastnost"
    );

    /**
     * Mapping from Czech boolean representations to standardized values
     */
    public static final Map<String, Boolean> CZECH_BOOLEAN_MAPPINGS = Map.of(
            "ano", true,
            "ne", false,
            "true", true,
            "false", false,
            "yes", true,
            "no", false
    );

    /**
     * Common XSD data type mappings
     */
    public static final Map<String, String> XSD_TYPE_MAPPINGS = Map.of(
            "string", "http://www.w3.org/2001/XMLSchema#string",
            "boolean", "http://www.w3.org/2001/XMLSchema#boolean",
            "integer", "http://www.w3.org/2001/XMLSchema#integer",
            "decimal", "http://www.w3.org/2001/XMLSchema#decimal",
            "date", "http://www.w3.org/2001/XMLSchema#date",
            "dateTime", "http://www.w3.org/2001/XMLSchema#dateTime",
            "anyURI", "http://www.w3.org/2001/XMLSchema#anyURI"
    );

    private TypeMappings() {
        // Utility class - prevent instantiation
    }

    /**
     * Maps Archimate element type to OFN ontology type.
     *
     * @param archiType the Archimate type identifier
     * @return the corresponding OFN type, or the original type if no mapping exists
     */
    public static String mapArchiType(String archiType) {
        if (archiType == null || archiType.trim().isEmpty()) {
            return archiType;
        }
        return ARCHIMATE_TO_OFN.getOrDefault(archiType.trim(), archiType);
    }

    /**
     * Checks if the given element type represents a class in the ontology.
     *
     * @param elementType the element type to check
     * @return true if the element type represents a class
     */
    public static boolean isClassType(String elementType) {
        if (elementType == null) {
            return false;
        }
        String trimmed = elementType.trim();
        return "typ subjektu".equals(trimmed) || "typ objektu".equals(trimmed);
    }

    /**
     * Checks if the given element type represents a property in the ontology.
     *
     * @param elementType the element type to check
     * @return true if the element type represents a property
     */
    public static boolean isPropertyType(String elementType) {
        if (elementType == null) {
            return false;
        }
        return "typ vlastnosti".equals(elementType.trim());
    }

    /**
     * Normalizes Czech boolean values to standard boolean.
     *
     * @param value the value to normalize
     * @return the normalized boolean value, or null if the value is not a recognized boolean
     */
    public static Boolean normalizeCzechBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return CZECH_BOOLEAN_MAPPINGS.get(value.trim().toLowerCase());
    }

    /**
     * Checks if a value represents a boolean in Czech or English.
     *
     * @param value the value to check
     * @return true if the value is a recognized boolean representation
     */
    public static boolean isBooleanValue(String value) {
        return normalizeCzechBoolean(value) != null;
    }

    /**
     * Maps a simple data type name to its full XSD URI.
     *
     * @param dataType the simple data type name
     * @return the full XSD URI, or the original value if no mapping exists
     */
    public static String mapToXSDType(String dataType) {
        if (dataType == null || dataType.trim().isEmpty()) {
            return dataType;
        }

        String trimmed = dataType.trim().toLowerCase();
        return XSD_TYPE_MAPPINGS.getOrDefault(trimmed, dataType);
    }
}
