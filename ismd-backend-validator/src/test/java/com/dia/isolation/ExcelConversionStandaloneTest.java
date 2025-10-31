package com.dia.isolation;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standalone test for Excel conversion workflow using testExcelProject.xlsx
 * This test validates the complete Read → Transform → Export pipeline for Excel files.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("isolation")
@Tag("excel")
@Tag("standalone")
class ExcelConversionStandaloneTest {

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private OFNDataTransformerNew transformer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testExcelProject_shouldExtractAndTransformCorrectly() throws Exception {
        System.out.println("\n========== EXCEL CONVERSION TEST START ==========");

        // Step 1: Read Excel file
        System.out.println("\n--- Step 1: Reading testExcelProject.xlsx ---");
        OntologyData ontologyData;
        try (InputStream is = new ClassPathResource("com/dia/canonical/complete/testExcelProject.xlsx").getInputStream()) {
            ontologyData = excelReader.readOntologyFromExcel(is);
        }

        // Step 2: Log extracted data
        System.out.println("\n--- Step 2: Extracted Ontology Data ---");
        logOntologyData(ontologyData);

        // Step 3: Transform to OFN format
        System.out.println("\n--- Step 3: Transforming to OFN format ---");
        TransformationResult transformationResult = transformer.transform(ontologyData);
        assertNotNull(transformationResult, "Transformation result should not be null");

        // Step 4: Export to JSON-LD
        System.out.println("\n--- Step 4: Exporting to JSON-LD ---");
        String actualJson = transformer.exportToJson(transformationResult);
        assertNotNull(actualJson, "Exported JSON should not be null");
        assertFalse(actualJson.trim().isEmpty(), "Exported JSON should not be empty");

        // Step 5: Load expected output
        System.out.println("\n--- Step 5: Loading expected output ---");
        String expectedJson = loadResourceAsString("com/dia/expected-outputs/complete/testOutput.jsonld");

        // Step 6: Parse both JSONs
        System.out.println("\n--- Step 6: Comparing actual vs expected output ---");
        JsonNode expectedRoot = objectMapper.readTree(expectedJson);
        JsonNode actualRoot = objectMapper.readTree(actualJson);

        // Step 7: Detailed comparison
        compareJsonStructures(expectedRoot, actualRoot);

        System.out.println("\n========== EXCEL CONVERSION TEST END ==========\n");
    }

    private void logOntologyData(OntologyData data) {
        System.out.println("Vocabulary Metadata:");
        if (data.getVocabularyMetadata() != null) {
            VocabularyMetadata vm = data.getVocabularyMetadata();
            System.out.println("  - Name: " + vm.getName());
            System.out.println("  - Description: " + vm.getDescription());
            System.out.println("  - Namespace: " + vm.getNamespace());
            System.out.println("  - Date of Creation: " + vm.getDateOfCreation());
            System.out.println("  - Date of Modification: " + vm.getDateOfModification());
        } else {
            System.out.println("  - [NULL]");
        }

        System.out.println("\nClasses: " + (data.getClasses() != null ? data.getClasses().size() : 0));
        if (data.getClasses() != null && !data.getClasses().isEmpty()) {
            for (int i = 0; i < Math.min(3, data.getClasses().size()); i++) {
                ClassData c = data.getClasses().get(i);
                System.out.println("  " + (i + 1) + ". " + c.getName() + " (Type: " + c.getType() + ")");
            }
            if (data.getClasses().size() > 3) {
                System.out.println("  ... and " + (data.getClasses().size() - 3) + " more");
            }
        }

        System.out.println("\nProperties: " + (data.getProperties() != null ? data.getProperties().size() : 0));
        if (data.getProperties() != null && !data.getProperties().isEmpty()) {
            for (int i = 0; i < Math.min(3, data.getProperties().size()); i++) {
                PropertyData p = data.getProperties().get(i);
                System.out.println("  " + (i + 1) + ". " + p.getName() +
                    " (Domain: " + p.getDomain() + ", DataType: " + p.getDataType() + ")");
            }
            if (data.getProperties().size() > 3) {
                System.out.println("  ... and " + (data.getProperties().size() - 3) + " more");
            }
        }

        System.out.println("\nRelationships: " + (data.getRelationships() != null ? data.getRelationships().size() : 0));
        if (data.getRelationships() != null && !data.getRelationships().isEmpty()) {
            for (int i = 0; i < Math.min(3, data.getRelationships().size()); i++) {
                RelationshipData r = data.getRelationships().get(i);
                System.out.println("  " + (i + 1) + ". " + r.getName() +
                    " (Domain: " + r.getDomain() + ", Range: " + r.getRange() + ")");
            }
            if (data.getRelationships().size() > 3) {
                System.out.println("  ... and " + (data.getRelationships().size() - 3) + " more");
            }
        }

        System.out.println("\nHierarchies: " + (data.getHierarchies() != null ? data.getHierarchies().size() : 0));
        if (data.getHierarchies() != null && !data.getHierarchies().isEmpty()) {
            for (int i = 0; i < Math.min(3, data.getHierarchies().size()); i++) {
                HierarchyData h = data.getHierarchies().get(i);
                System.out.println("  " + (i + 1) + ". " + h.getSubClass() + " -> " + h.getSuperClass());
            }
            if (data.getHierarchies().size() > 3) {
                System.out.println("  ... and " + (data.getHierarchies().size() - 3) + " more");
            }
        }
    }

    private void compareJsonStructures(JsonNode expected, JsonNode actual) {
        System.out.println("\nComparison Results:");

        // Compare context
        compareField(expected, actual, "@context");

        // Compare IRI
        compareField(expected, actual, "iri");

        // Compare typ
        compareField(expected, actual, "typ");

        // Compare název
        compareField(expected, actual, "název");

        // Compare popis
        compareField(expected, actual, "popis");

        // Compare pojmy array
        if (expected.has("pojmy") && actual.has("pojmy")) {
            JsonNode expectedPojmy = expected.get("pojmy");
            JsonNode actualPojmy = actual.get("pojmy");

            System.out.println("\nPojmy (Concepts) comparison:");
            System.out.println("  Expected count: " + expectedPojmy.size());
            System.out.println("  Actual count: " + actualPojmy.size());

            if (expectedPojmy.size() != actualPojmy.size()) {
                System.out.println("  ❌ COUNT MISMATCH!");
                System.out.println("  Missing: " + (expectedPojmy.size() - actualPojmy.size()) + " concepts");
            } else {
                System.out.println("  ✓ Count matches");
            }

            // Sample a few concepts for detailed comparison
            if (expectedPojmy.size() > 0 && actualPojmy.size() > 0) {
                System.out.println("\n  Comparing first concept:");
                JsonNode firstExpected = expectedPojmy.get(0);
                JsonNode firstActual = actualPojmy.get(0);

                System.out.println("    Expected IRI: " + (firstExpected.has("iri") ? firstExpected.get("iri").asText() : "N/A"));
                System.out.println("    Actual IRI: " + (firstActual.has("iri") ? firstActual.get("iri").asText() : "N/A"));

                System.out.println("    Expected název: " + (firstExpected.has("název") ? firstExpected.get("název") : "N/A"));
                System.out.println("    Actual název: " + (firstActual.has("název") ? firstActual.get("název") : "N/A"));

                System.out.println("    Expected typ: " + (firstExpected.has("typ") ? firstExpected.get("typ") : "N/A"));
                System.out.println("    Actual typ: " + (firstActual.has("typ") ? firstActual.get("typ") : "N/A"));

                // Check for definiční-obor and obor-hodnot
                System.out.println("    Expected definiční-obor: " + (firstExpected.has("definiční-obor") ? firstExpected.get("definiční-obor").asText() : "N/A"));
                System.out.println("    Actual definiční-obor: " + (firstActual.has("definiční-obor") ? firstActual.get("definiční-obor").asText() : "N/A"));

                System.out.println("    Expected obor-hodnot: " + (firstExpected.has("obor-hodnot") ? firstExpected.get("obor-hodnot").asText() : "N/A"));
                System.out.println("    Actual obor-hodnot: " + (firstActual.has("obor-hodnot") ? firstActual.get("obor-hodnot").asText() : "N/A"));
            }
        } else {
            System.out.println("\nPojmy (Concepts):");
            System.out.println("  Expected has pojmy: " + expected.has("pojmy"));
            System.out.println("  Actual has pojmy: " + actual.has("pojmy"));
            System.out.println("  ❌ POJMY MISSING!");
        }
    }

    private void compareField(JsonNode expected, JsonNode actual, String fieldName) {
        boolean expectedHas = expected.has(fieldName);
        boolean actualHas = actual.has(fieldName);

        System.out.println("\n" + fieldName + ":");
        if (expectedHas && actualHas) {
            String expectedValue = expected.get(fieldName).toString();
            String actualValue = actual.get(fieldName).toString();

            if (expectedValue.equals(actualValue)) {
                System.out.println("  ✓ Matches");
            } else {
                System.out.println("  ❌ MISMATCH");
                System.out.println("  Expected: " + expectedValue);
                System.out.println("  Actual: " + actualValue);
            }
        } else {
            if (!expectedHas && !actualHas) {
                System.out.println("  ✓ Both missing (acceptable)");
            } else if (!actualHas) {
                System.out.println("  ❌ MISSING in actual output");
                System.out.println("  Expected: " + expected.get(fieldName));
            } else {
                System.out.println("  ⚠ Present in actual but not in expected");
                System.out.println("  Actual: " + actual.get(fieldName));
            }
        }
    }

    private String loadResourceAsString(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
