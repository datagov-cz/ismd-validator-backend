package com.dia.utility;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dia.constants.ArchiConstants.XSD;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@UtilityClass
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

    public String cleanForXMLName(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String cleaned = input.replaceAll("[^a-zA-Z0-9]", "");

        if (!cleaned.isEmpty() && Character.isDigit(cleaned.charAt(0))) {
            cleaned = "n" + cleaned;
        }

        return cleaned;
    }

    public boolean isValidXMLNameStart(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        if (!Character.isLetter(name.charAt(0))) {
            return false;
        }

        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }

        return true;
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

    public boolean isValidAgendaValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        value = value.trim();

        if (value.matches("^(\\d+)$")) {
            return true;
        }

        if (value.matches("^A(\\d+)$")) {
            return true;
        }

        return value.matches("^(https://rpp-opendata\\.egon\\.gov\\.cz/odrpp/zdroj/agenda/A)(\\d+)$");
    }

    public String transformAgendaValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        value = value.trim();

        if (value.matches("^(\\d+)$")) {
            return "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A" + value;
        }

        if (value.matches("^A(\\d+)$")) {
            return "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/" + value;
        }

        return value;
    }

    public boolean isValidAISValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        value = value.trim();

        if (value.matches("^(\\d+)$")) {
            return true;
        }

        return value.matches("^(https://rpp-opendata\\.egon\\.gov\\.cz/odrpp/zdroj/isvs/)(\\d+)$");
    }

    public String transformAISValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        value = value.trim();

        if (value.matches("^(\\d+)$")) {
            return "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/" + value;
        }

        return value;
    }
}
