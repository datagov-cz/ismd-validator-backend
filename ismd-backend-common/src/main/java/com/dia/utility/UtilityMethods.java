package com.dia.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dia.constants.ArchiOntologyConstants.XSD;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@UtilityClass
@Slf4j
public class UtilityMethods {

    public boolean isValidFirstChar(char ch) {
        return isLetterOrUnderscore(ch) || Character.isDigit(ch) || ch == ':';
    }

    public boolean isValidSubsequentChar(char ch) {
        return isLetterOrUnderscore(ch) || Character.isDigit(ch) || ch == '-' ||
                ch == '.' || ch == ':' ||
                ch == '·' ||
                (ch >= '̀' && ch <= 'ͯ') ||
                (ch >= '‿' && ch <= '⁀');
    }

    public boolean isLetterOrUnderscore(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    public boolean isValidUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String ensureNamespaceEndsWithDelimiter(String namespace) {
        if (!namespace.endsWith("/") && !namespace.endsWith("#")) {
            return namespace + "/";
        }
        return namespace;
    }

    public boolean looksLikeId(String name) {
        return name != null &&
                (name.matches("id-[0-9a-f]{8}.*") ||
                        name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    public String sanitizeForIRI(String input) {
        if (input == null || input.isEmpty()) {
            return "unnamed";
        }

        String lowercased = input.toLowerCase();

        StringBuilder result = new StringBuilder();
        boolean isFirst = true;

        for (int i = 0; i < lowercased.length(); i++) {
            char ch = lowercased.charAt(i);

            if (isFirst) {
                if (UtilityMethods.isValidFirstChar(ch)) {
                    result.append(ch);
                    isFirst = false;
                } else {
                    result.append('-');
                }
            } else {
                if (UtilityMethods.isValidSubsequentChar(ch)) {
                    result.append(ch);
                } else {
                    result.append('-');
                }
            }
        }

        String sanitized = result.toString().replaceAll("-+", "-");
        sanitized = sanitized.replaceAll("-+$", "");
        sanitized = sanitized.replaceAll("^-+", "");

        return sanitized.isEmpty() ? "unnamed" : sanitized;
    }

    public Map<String, Object> filterMap(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();

        map.forEach((key, value) -> {
            Object filtered = filterValue(value);
            if (filtered != null) {
                result.put(key, filtered);
            }
        });

        return result;
    }

    public List<Object> filterList(List<?> list) {
        List<Object> result = new ArrayList<>();

        for (Object item : list) {
            Object filtered = filterValue(item);
            if (filtered != null) {
                result.add(filtered);
            }
        }

        return result;
    }

    public Object filterValue(Object value) {
        if (isEmpty(value)) return null;

        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> filtered = filterMap((Map<String, Object>) map);
            return filtered.isEmpty() ? null : filtered;
        } else if (value instanceof List<?> list) {
            List<Object> filtered = filterList(list);
            return filtered.isEmpty() ? null : filtered;
        }

        return value;
    }

    public String transformEliUrl(String url, Boolean removeELI) {
        if (Boolean.FALSE.equals(removeELI)) {
            return url;
        }

        Pattern eliPattern = Pattern.compile(".*?(eli/cz/sb/.*)$");
        Matcher matcher = eliPattern.matcher(url);

        if (matcher.matches()) {
            String eliPart = matcher.group(1);
            return "https://opendata.eselpoint.cz/esel-esb/" + eliPart;
        } else {
            return url;
        }
    }

    public String mapDataTypeToXSD(String dataType) {
        String normalized = dataType.toLowerCase().trim();

        return switch (normalized) {
            case "string", "text", "řetězec" -> XSD + "string";
            case "boolean", "bool", "logický" -> XSD + "boolean";
            case "integer", "int", "celé číslo" -> XSD + "integer";
            case "decimal", "float", "double", "desetinné číslo" -> XSD + "decimal";
            case "date", "datum" -> XSD + "date";
            case "time", "čas" -> XSD + "time";
            case "datetime", "datum a čas" -> XSD + "dateTime";
            case "uri", "url", "anyuri" -> XSD + "anyURI";
            default -> XSD + "string"; // Safe fallback
        };
    }
}
