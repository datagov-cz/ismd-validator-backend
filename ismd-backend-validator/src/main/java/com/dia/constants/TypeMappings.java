package com.dia.constants;

import java.util.Map;

public final class TypeMappings {

    public static final Map<String, String> ARCHIMATE_TO_OFN = Map.of(
            "typ subjektu", "Subjekt práva",
            "typ objektu", "Objekt práva",
            "typ vlastnosti", "Vlastnost"
    );

    public static final Map<String, Boolean> CZECH_BOOLEAN_MAPPINGS = Map.of(
            "ano", true,
            "ne", false,
            "true", true,
            "false", false,
            "yes", true,
            "no", false
    );

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
    }

    public static String mapArchiType(String archiType) {
        if (archiType == null || archiType.trim().isEmpty()) {
            return archiType;
        }
        return ARCHIMATE_TO_OFN.getOrDefault(archiType.trim(), archiType);
    }

    public static boolean isPropertyType(String elementType) {
        if (elementType == null || elementType.trim().isEmpty()) {
            return false;
        }

        String trimmedType = elementType.trim();

        return "typ vlastnosti".equals(trimmedType) ||
                trimmedType.toLowerCase().contains("vlastnost");
    }

    public static Boolean normalizeCzechBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return CZECH_BOOLEAN_MAPPINGS.get(value.trim().toLowerCase());
    }

    public static boolean isBooleanValue(String value) {
        return normalizeCzechBoolean(value) != null;
    }

    public static String mapToXSDType(String dataType) {
        if (dataType == null || dataType.trim().isEmpty()) {
            return dataType;
        }

        String trimmed = dataType.trim().toLowerCase();
        return XSD_TYPE_MAPPINGS.getOrDefault(trimmed, dataType);
    }
}
