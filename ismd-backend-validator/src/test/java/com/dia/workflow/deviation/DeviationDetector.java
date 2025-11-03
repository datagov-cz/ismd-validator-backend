package com.dia.workflow.deviation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Detects and reports deviations between expected and actual JSON-LD outputs.
 * Provides detailed location information to isolate where in the workflow deviations occurred.
 */
public class DeviationDetector {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<WorkflowDeviation> deviations = new ArrayList<>();

    /**
     * Detects all deviations between expected and actual JSON outputs
     */
    public List<WorkflowDeviation> detectDeviations(String expectedJson, String actualJson) throws Exception {
        deviations.clear();

        JsonNode expected = objectMapper.readTree(expectedJson);
        JsonNode actual = objectMapper.readTree(actualJson);

        compareNodes(expected, actual, "root");

        return new ArrayList<>(deviations);
    }

    /**
     * Prints a formatted deviation report to console
     */
    public void printDeviationReport(List<WorkflowDeviation> deviations) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEVIATION REPORT");
        System.out.println("=".repeat(80));

        Map<WorkflowDeviation.DeviationType, List<WorkflowDeviation>> byType = new EnumMap<>(WorkflowDeviation.DeviationType.class);
        for (WorkflowDeviation deviation : deviations) {
            byType.computeIfAbsent(deviation.getType(), k -> new ArrayList<>()).add(deviation);
        }

        for (WorkflowDeviation.DeviationType type : WorkflowDeviation.DeviationType.values()) {
            List<WorkflowDeviation> items = byType.get(type);
            if (items != null && !items.isEmpty()) {
                System.out.println("\n" + type + " (" + items.size() + " deviation(s)):");
                System.out.println("-".repeat(80));
                for (int i = 0; i < items.size(); i++) {
                    System.out.println("\n" + (i + 1) + ". " + items.get(i));
                }
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Total deviations: " + deviations.size());
        System.out.println("=".repeat(80) + "\n");
    }

    /**
     * Recursively compares two JSON nodes and records deviations
     */
    private void compareNodes(JsonNode expected, JsonNode actual, String path) {
        if (expected == null && actual == null) {
            return;
        }

        if (expected == null) {
            addDeviation(WorkflowDeviation.builder()
                .type(WorkflowDeviation.DeviationType.EXTRA_FIELD)
                .location(path)
                .message("Field present in actual but not in expected")
                .actualValue(actual.toString())
                .severity(WorkflowDeviation.Severity.MEDIUM)
                .build());
            return;
        }

        if (actual == null) {
            addDeviation(WorkflowDeviation.builder()
                .type(WorkflowDeviation.DeviationType.MISSING_FIELD)
                .location(path)
                .message("Field present in expected but missing in actual")
                .expectedValue(expected.toString())
                .severity(WorkflowDeviation.Severity.HIGH)
                .build());
            return;
        }

        if (expected.getNodeType() != actual.getNodeType()) {
            addDeviation(WorkflowDeviation.builder()
                .type(WorkflowDeviation.DeviationType.TYPE_MISMATCH)
                .location(path)
                .message("Node types differ: expected " + expected.getNodeType() + ", actual " + actual.getNodeType())
                .expectedValue(expected.getNodeType())
                .actualValue(actual.getNodeType())
                .severity(WorkflowDeviation.Severity.HIGH)
                .build());
            return;
        }

        if (expected.isObject()) {
            compareObjects(expected, actual, path);
        } else if (expected.isArray()) {
            compareArrays(expected, actual, path);
        } else if (expected.isValueNode()) {
            compareValues(expected, actual, path);
        }
    }

    /**
     * Compares two JSON objects
     */
    private void compareObjects(JsonNode expected, JsonNode actual, String path) {
        Iterator<String> expectedFields = expected.fieldNames();
        while (expectedFields.hasNext()) {
            String fieldName = expectedFields.next();
            String fieldPath = path + "." + fieldName;

            if (!actual.has(fieldName)) {
                if (isOptionalField(fieldName)) {
                    addDeviation(WorkflowDeviation.builder()
                        .type(WorkflowDeviation.DeviationType.MISSING_FIELD)
                        .location(fieldPath)
                        .message("Optional field missing in actual output")
                        .expectedValue(expected.get(fieldName).toString())
                        .severity(WorkflowDeviation.Severity.LOW)
                        .build());
                } else {
                    addDeviation(WorkflowDeviation.builder()
                        .type(WorkflowDeviation.DeviationType.MISSING_FIELD)
                        .location(fieldPath)
                        .message("Required field missing in actual output")
                        .expectedValue(expected.get(fieldName).toString())
                        .severity(WorkflowDeviation.Severity.HIGH)
                        .build());
                }
            } else {
                compareNodes(expected.get(fieldName), actual.get(fieldName), fieldPath);
            }
        }

        Iterator<String> actualFields = actual.fieldNames();
        while (actualFields.hasNext()) {
            String fieldName = actualFields.next();
            String fieldPath = path + "." + fieldName;

            if (!expected.has(fieldName)) {
                addDeviation(WorkflowDeviation.builder()
                    .type(WorkflowDeviation.DeviationType.EXTRA_FIELD)
                    .location(fieldPath)
                    .message("Field present in actual but not in expected")
                    .actualValue(actual.get(fieldName).toString())
                    .severity(WorkflowDeviation.Severity.MEDIUM)
                    .build());
            }
        }
    }

    /**
     * Compares two JSON arrays
     */
    private void compareArrays(JsonNode expected, JsonNode actual, String path) {
        if (expected.size() != actual.size()) {
            addDeviation(WorkflowDeviation.builder()
                .type(WorkflowDeviation.DeviationType.COUNT_MISMATCH)
                .location(path)
                .message(String.format("Array size mismatch: expected %d elements, actual %d elements",
                    expected.size(), actual.size()))
                .expectedValue(expected.size())
                .actualValue(actual.size())
                .severity(WorkflowDeviation.Severity.HIGH)
                .build());
        }

        if (path.endsWith("pojmy")) {
            comparePojmyArrays(expected, actual, path);
        } else {
            int minSize = Math.min(expected.size(), actual.size());
            for (int i = 0; i < minSize; i++) {
                String elementPath = path + "[" + i + "]";
                compareNodes(expected.get(i), actual.get(i), elementPath);
            }
        }
    }

    /**
     * Compares pojmy (concepts) arrays by matching IRIs
     */
    private void comparePojmyArrays(JsonNode expected, JsonNode actual, String path) {
        Map<String, JsonNode> expectedByIri = buildIriMap(expected);
        Map<String, JsonNode> actualByIri = buildIriMap(actual);

        for (Map.Entry<String, JsonNode> entry : expectedByIri.entrySet()) {
            String iri = entry.getKey();
            JsonNode expectedPojem = entry.getValue();

            if (!actualByIri.containsKey(iri)) {
                addDeviation(WorkflowDeviation.builder()
                    .type(WorkflowDeviation.DeviationType.MISSING_FIELD)
                    .location(path + "[iri=" + iri + "]")
                    .message("Concept with IRI '" + iri + "' present in expected but missing in actual")
                    .expectedValue(expectedPojem.toString())
                    .severity(WorkflowDeviation.Severity.CRITICAL)
                    .build());
            } else {
                JsonNode actualPojem = actualByIri.get(iri);
                compareNodes(expectedPojem, actualPojem, path + "[iri=" + iri + "]");
            }
        }

        for (String iri : actualByIri.keySet()) {
            if (!expectedByIri.containsKey(iri)) {
                addDeviation(WorkflowDeviation.builder()
                    .type(WorkflowDeviation.DeviationType.EXTRA_FIELD)
                    .location(path + "[iri=" + iri + "]")
                    .message("Concept with IRI '" + iri + "' present in actual but not in expected")
                    .actualValue(actualByIri.get(iri).toString())
                    .severity(WorkflowDeviation.Severity.MEDIUM)
                    .build());
            }
        }
    }

    /**
     * Compares two value nodes
     */
    private void compareValues(JsonNode expected, JsonNode actual, String path) {
        if (path.equals("root.@context") && expected.isTextual() && actual.isTextual()) {
            String expectedText = expected.asText();
            String actualText = actual.asText();

            if (expectedText.contains("ofn.gov.cz") && actualText.contains("ofn.gov.cz")) {
                return;
            }
        }

        if (!expected.equals(actual)) {
            addDeviation(WorkflowDeviation.builder()
                .type(WorkflowDeviation.DeviationType.VALUE_MISMATCH)
                .location(path)
                .message("Value mismatch")
                .expectedValue(formatValueForDisplay(expected))
                .actualValue(formatValueForDisplay(actual))
                .build());
        }
    }

    /**
     * Builds a map of IRI -> JsonNode for concepts
     */
    private Map<String, JsonNode> buildIriMap(JsonNode array) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode element : array) {
            if (element.has("iri")) {
                String iri = element.get("iri").asText();
                map.put(iri, element);
            }
        }
        return map;
    }

    /**
     * Determines if a field is optional based on field name
     */
    private boolean isOptionalField(String fieldName) {
        Set<String> optionalFields = Set.of(
            "alternativní-název",
            "popis",
            "definice",
            "definující-nelegislativní-zdroj",
            "související-nelegislativní-zdroj",
            "ekvivalentní-pojem",
            "nadřazený-pojem",
            "nadřazená-třída",
            "nadřazená-vlastnost",
            "agenda",
            "agendový-informační-systém",
            "je-sdílen-v-ppdf"
        );
        return optionalFields.contains(fieldName);
    }

    /**
     * Formats a value for display in deviation reports
     */
    private String formatValueForDisplay(JsonNode node) {
        if (node.isTextual()) {
            return "\"" + node.asText() + "\"";
        } else if (node.isNumber()) {
            return node.asText();
        } else if (node.isBoolean()) {
            return String.valueOf(node.asBoolean());
        } else if (node.isNull()) {
            return "null";
        } else {
            String str = node.toString();
            if (str.length() > 100) {
                return str.substring(0, 97) + "...";
            }
            return str;
        }
    }

    /**
     * Adds a deviation to the list
     */
    private void addDeviation(WorkflowDeviation deviation) {
        deviations.add(deviation);
    }
}
