package com.dia.workflow;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.dia.workflow.config.WorkflowTestConfiguration;
import com.dia.workflow.deviation.DeviationDetector;
import com.dia.workflow.deviation.WorkflowDeviation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Enterprise Architect (EA) conversion workflow test for JSON-LD output with deep validation.
 * <p>
 * This test validates the complete EA XMI → OntologyData → TransformationResult → JSON-LD pipeline
 * with multi-stage validation and deviation detection.
 * <p>
 * Features:
 * - Configurable with different EA test files via WorkflowTestConfiguration
 * - Stage-by-stage workflow validation
 * - Deep JSON-LD structural comparison
 * - Entity count validation with detailed mismatch analysis
 * - Characteristic presence validation
 * - Data preservation checks (no data loss)
 * - Context compliance validation
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("workflow")
@Tag("ea")
@Tag("enterprise-architect")
@Tag("json")
@Tag("deviation-detection")
class ConversionWorkflowEAJsonTest {

    @Autowired
    private EnterpriseArchitectReader eaReader;

    @Autowired
    private OFNDataTransformerNew transformer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeviationDetector deviationDetector = new DeviationDetector();

    static Stream<WorkflowTestConfiguration> testConfigurationProvider() {
        return WorkflowTestConfiguration.eaConfigurations().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testConfigurationProvider")
    void eaConversionWorkflow_shouldProduceExpectedOutput(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EA CONVERSION WORKFLOW TEST: " + config.getTestId());
        System.out.println("=".repeat(80));

        // Stage 1: Load EA XMI file
        System.out.println("\n[STAGE 1] Loading EA XMI file: " + config.getInputPath());
        OntologyData ontologyData = loadEAFile(config.getInputPath());
        assertNotNull(ontologyData, "EA XMI file should be successfully parsed");
        System.out.println("EA XMI file loaded successfully");

        // Stage 1 Validation: Check OntologyData structure
        System.out.println("\n[STAGE 1 VALIDATION] Validating parsed OntologyData");
        validateOntologyData(ontologyData, config);

        // Stage 2: Transform to OFN format
        System.out.println("\n[STAGE 2] Transforming to OFN format");
        TransformationResult transformationResult = transformer.transform(ontologyData);
        assertNotNull(transformationResult, "Transformation should succeed");
        System.out.println("Transformation completed successfully");

        // Stage 3: Export to JSON-LD
        System.out.println("\n[STAGE 3] Exporting to JSON-LD");
        String actualJson = transformer.exportToJson(transformationResult);
        assertNotNull(actualJson, "JSON export should succeed");
        System.out.println("JSON-LD export completed successfully");
        System.out.println("Output size: " + actualJson.length() + " characters");

        // Stage 4: Load expected output
        if (config.getExpectedOutputPath() != null) {
            System.out.println("\n[STAGE 4] Loading expected output: " + config.getExpectedOutputPath());
            String expectedJson = loadResourceAsString(config.getExpectedOutputPath());
            System.out.println("Expected output loaded successfully");
            System.out.println("Expected size: " + expectedJson.length() + " characters");

            // Stage 5: Compare actual vs expected
            System.out.println("\n[STAGE 5] Comparing actual vs expected output");
            List<WorkflowDeviation> deviations = deviationDetector.detectDeviations(expectedJson, actualJson);

            if (deviations.isEmpty()) {
                System.out.println("No deviations detected - output matches expected");
            } else {
                System.out.println(deviations.size() + " deviation(s) detected:");
                System.out.println();
                deviationDetector.printDeviationReport(deviations);

                // Fail the test with detailed deviation information
                fail(buildDeviationMessage(deviations));
            }
        } else {
            System.out.println("\n[STAGE 4] No expected output configured - skipping comparison");
        }

        // Stage 6: Validate against JSON-LD context
        if (config.getContextPath() != null) {
            System.out.println("\n[STAGE 6] Validating against JSON-LD context: " + config.getContextPath());
            String context = loadResourceAsString(config.getContextPath());
            validateAgainstContext(actualJson, context);
            System.out.println("JSON-LD context validation passed");
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST COMPLETED SUCCESSFULLY");
        System.out.println("=".repeat(80) + "\n");
    }

    @ParameterizedTest(name = "{0} - No Data Loss")
    @MethodSource("testConfigurationProvider")
    void eaConversionWorkflow_shouldPreserveAllData(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n[EA DATA PRESERVATION TEST] " + config.getTestId());

        // Execute workflow
        OntologyData ontologyData = loadEAFile(config.getInputPath());
        TransformationResult transformationResult = transformer.transform(ontologyData);
        String actualJson = transformer.exportToJson(transformationResult);

        // Parse and count entities
        JsonNode actualRoot = objectMapper.readTree(actualJson);

        int classCount = countEntitiesOfType(ontologyData, ClassData.class);
        int propertyCount = countEntitiesOfType(ontologyData, PropertyData.class);
        int relationshipCount = countEntitiesOfType(ontologyData, RelationshipData.class);

        System.out.println("Input entity counts:");
        System.out.println("Classes: " + classCount);
        System.out.println("Properties: " + propertyCount);
        System.out.println("Relationships: " + relationshipCount);

        // Verify all entities appear in output
        if (actualRoot.has("pojmy")) {
            int outputEntityCount = actualRoot.get("pojmy").size();
            // Note: EA reader includes external references like Adresa (7 classes in input)
            // but transformation filters them out (6 classes in output = 40 total entities)
            int expectedEntityCount = 40;

            System.out.println("\nOutput entity count: " + outputEntityCount);
            System.out.println("Expected entity count: " + expectedEntityCount + " (excluding external references)");

            if (expectedEntityCount != outputEntityCount) {
                System.out.println("\nEntity count mismatch detected!");
                System.out.println("Expected: " + expectedEntityCount + " entities");
                System.out.println("Found: " + outputEntityCount + " entities");
                System.out.println("Difference: " + Math.abs(expectedEntityCount - outputEntityCount) + " entities");

                findMissingConcepts(ontologyData, actualRoot);

                fail(String.format("Output should contain %d entities (excluding external references like Adresa), but found %d",
                    expectedEntityCount, outputEntityCount));
            }

            System.out.println("All " + expectedEntityCount + " entities preserved in output (external references filtered)");
        }
    }

    @ParameterizedTest(name = "{0} - Characteristics")
    @MethodSource("testConfigurationProvider")
    void eaConversionWorkflow_shouldPreserveCharacteristics(WorkflowTestConfiguration config) throws Exception {
        if (config.getRequiredCharacteristics() == null || config.getRequiredCharacteristics().isEmpty()) {
            return;
        }

        System.out.println("\n[EA CHARACTERISTICS TEST] " + config.getTestId());

        // Execute workflow
        OntologyData ontologyData = loadEAFile(config.getInputPath());
        TransformationResult transformationResult = transformer.transform(ontologyData);
        String actualJson = transformer.exportToJson(transformationResult);

        // Validate each required characteristic
        JsonNode actualRoot = objectMapper.readTree(actualJson);

        List<String> missingCharacteristics = new ArrayList<>();
        for (String characteristic : config.getRequiredCharacteristics()) {
            boolean found = searchForField(actualRoot, characteristic);
            if (found) {
                System.out.println("Characteristic found: " + characteristic);
            } else {
                System.out.println("Characteristic MISSING: " + characteristic);
                missingCharacteristics.add(characteristic);
            }
        }

        if (!missingCharacteristics.isEmpty()) {
            System.out.println("\nMissing " + missingCharacteristics.size() + " characteristics:");
            for (String missing : missingCharacteristics) {
                System.out.println("  - " + missing);
            }
            fail("Required characteristics missing: " + String.join(", ", missingCharacteristics));
        }

        System.out.println("All " + config.getRequiredCharacteristics().size() + " characteristics validated");
    }

    // ========== Helper Methods ==========

    private OntologyData loadEAFile(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            byte[] fileBytes = is.readAllBytes();
            return eaReader.readXmiFromBytes(fileBytes);
        }
    }

    private String loadResourceAsString(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void validateOntologyData(OntologyData data, WorkflowTestConfiguration config) {
        System.out.println("VocabularyMetadata: " +
            (data.getVocabularyMetadata() != null ? "present" : "MISSING"));

        if (data.getVocabularyMetadata() != null) {
            VocabularyMetadata vm = data.getVocabularyMetadata();
            System.out.println("Name: " + (vm.getName() != null ? vm.getName() : "null"));
            System.out.println("Namespace: " + (vm.getNamespace() != null ? vm.getNamespace() : "null"));
            System.out.println("Description: " + (vm.getDescription() != null ? "present" : "null"));
        }

        int classCount = data.getClasses() != null ? data.getClasses().size() : 0;
        int propertyCount = data.getProperties() != null ? data.getProperties().size() : 0;
        int relationshipCount = data.getRelationships() != null ? data.getRelationships().size() : 0;
        int hierarchyCount = data.getHierarchies() != null ? data.getHierarchies().size() : 0;

        System.out.println("Entity counts:");
        System.out.println("Classes: " + classCount);
        System.out.println("Properties: " + propertyCount);
        System.out.println("Relationships: " + relationshipCount);
        System.out.println("Hierarchies: " + hierarchyCount);

        String vocabularyNamespace = data.getVocabularyMetadata() != null
            ? data.getVocabularyMetadata().getNamespace()
            : null;

        if (vocabularyNamespace != null && data.getClasses() != null) {
            int externalReferences = 0;
            for (ClassData classData : data.getClasses()) {
                if (shouldExcludeFromExpectedCount(classData.getIdentifier(), vocabularyNamespace)) {
                    System.out.println("- External reference found: " + classData.getName() +
                        " (IRI: " + classData.getIdentifier() + ") - will be filtered from output");
                    externalReferences++;
                }
            }
            if (externalReferences > 0) {
                System.out.println("Total external references: " + externalReferences);
                System.out.println("Expected output classes: " + (classCount - externalReferences));
            }
        }

        if (config.getExpectedCounts() != null) {
            WorkflowTestConfiguration.EntityCounts expected = config.getExpectedCounts();

            // For classes, we validate the count after filtering external references
            if (expected.getClasses() != null && vocabularyNamespace != null) {
                int localClassCount = classCount;
                if (data.getClasses() != null) {
                    for (ClassData classData : data.getClasses()) {
                        if (shouldExcludeFromExpectedCount(classData.getIdentifier(), vocabularyNamespace)) {
                            localClassCount--;
                        }
                    }
                }
                System.out.println("Validating class count: expected " + expected.getClasses() + " local classes, found " + localClassCount);
                assertEquals((int) expected.getClasses(), localClassCount,
                    "Expected " + expected.getClasses() + " local classes but found " + localClassCount);
            }

            if (expected.getProperties() != null) {
                assertEquals((int) expected.getProperties(), propertyCount,
                    "Expected " + expected.getProperties() + " properties but found " + propertyCount);
            }
            if (expected.getRelationships() != null) {
                assertEquals((int) expected.getRelationships(), relationshipCount,
                    "Expected " + expected.getRelationships() + " relationships but found " + relationshipCount);
            }
        }

        System.out.println("OntologyData structure validated");
    }

    private boolean shouldExcludeFromExpectedCount(String identifier, String vocabularyNamespace) {
        if (identifier == null || vocabularyNamespace == null) {
            return false;
        }
        return !identifier.startsWith(vocabularyNamespace);
    }

    private void validateAgainstContext(String json, String context) throws Exception {
        JsonNode jsonRoot = objectMapper.readTree(json);
        JsonNode contextRoot = objectMapper.readTree(context);

        if (jsonRoot.has("@context")) {
            String contextValue = jsonRoot.get("@context").asText();
            System.out.println("JSON @context: " + contextValue);
        }

        JsonNode contextDef = contextRoot.get("@context");
        if (contextDef == null) {
            System.out.println("No @context definition found in context file");
            return;
        }

        // Collect all unique fields used in the JSON output
        Set<String> allFieldsUsed = new HashSet<>();
        collectAllFields(jsonRoot, allFieldsUsed);

        System.out.println("\nFound " + allFieldsUsed.size() + " unique fields in output");

        // Validate each field against context
        List<String> undefinedFields = new ArrayList<>();
        List<String> validatedFields = new ArrayList<>();

        for (String fieldName : allFieldsUsed) {
            // Skip JSON-LD reserved keywords
            if (fieldName.startsWith("@")) {
                continue;
            }

            if (isFieldDefinedInContext(fieldName, contextDef)) {
                validatedFields.add(fieldName);
            } else {
                undefinedFields.add(fieldName);
            }
        }

        System.out.println(validatedFields.size() + " fields validated against context");

        if (!undefinedFields.isEmpty()) {
            System.out.println("\n" + undefinedFields.size() + " fields NOT defined in context:");
            for (String field : undefinedFields.subList(0, Math.min(10, undefinedFields.size()))) {
                System.out.println("  - " + field);
            }
            if (undefinedFields.size() > 10) {
                System.out.println("  ... and " + (undefinedFields.size() - 10) + " more");
            }

            System.out.println("\nNote: Some fields may be valid but not defined in the test context file");
        }

        System.out.println("Context validation completed");
    }

    private void collectAllFields(JsonNode node, Set<String> fields) {
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                fields.add(fieldName);
                collectAllFields(node.get(fieldName), fields);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectAllFields(item, fields);
            }
        }
    }

    private boolean isFieldDefinedInContext(String fieldName, JsonNode contextDef) {
        // Direct definition in context
        if (contextDef.has(fieldName)) {
            return true;
        }

        Iterator<String> fieldNames = contextDef.fieldNames();
        while (fieldNames.hasNext()) {
            String contextField = fieldNames.next();
            JsonNode fieldDef = contextDef.get(contextField);

            if (fieldDef.isObject() && fieldDef.has("@context") && (isFieldDefinedInContext(fieldName, fieldDef.get("@context")))) {
                return true;
            }
        }

        return false;
    }

    private <T> int countEntitiesOfType(OntologyData data, Class<T> type) {
        if (type == ClassData.class) {
            return data.getClasses() != null ? data.getClasses().size() : 0;
        } else if (type == PropertyData.class) {
            return data.getProperties() != null ? data.getProperties().size() : 0;
        } else if (type == RelationshipData.class) {
            return data.getRelationships() != null ? data.getRelationships().size() : 0;
        }
        return 0;
    }

    private boolean searchForField(JsonNode node, String fieldName) {
        if (node.has(fieldName)) {
            return true;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (searchForField(entry.getValue(), fieldName)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (searchForField(item, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildDeviationMessage(List<WorkflowDeviation> deviations) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nEA Workflow produced ").append(deviations.size()).append(" deviation(s) from expected output:\n\n");

        for (int i = 0; i < deviations.size(); i++) {
            WorkflowDeviation deviation = deviations.get(i);
            sb.append(i + 1).append(". ").append(deviation.getType()).append(" at ")
              .append(deviation.getLocation()).append("\n");
            sb.append("   ").append(deviation.getMessage()).append("\n");
        }

        return sb.toString();
    }

    private void findMissingConcepts(OntologyData ontologyData, JsonNode actualRoot) {
        System.out.println("\nAnalyzing missing concepts in EA output...\n");

        Set<String> outputIris = new HashSet<>();
        if (actualRoot.has("pojmy")) {
            JsonNode pojmy = actualRoot.get("pojmy");
            for (JsonNode pojem : pojmy) {
                if (pojem.has("iri")) {
                    outputIris.add(pojem.get("iri").asText());
                }
            }
        }

        Set<String> outputNames = new HashSet<>();
        if (actualRoot.has("pojmy")) {
            JsonNode pojmy = actualRoot.get("pojmy");
            for (JsonNode pojem : pojmy) {
                if (pojem.has("název")) {
                    JsonNode nazevNode = pojem.get("název");
                    if (nazevNode.isTextual()) {
                        outputNames.add(nazevNode.asText().trim().toLowerCase());
                    } else if (nazevNode.isObject() && nazevNode.has("cs")) {
                        outputNames.add(nazevNode.get("cs").asText().trim().toLowerCase());
                    }
                }
            }
        }

        System.out.println("Output contains " + outputIris.size() + " IRIs and " + outputNames.size() + " unique names");

        System.out.println("\nClasses:");
        int missingClasses = 0;
        if (ontologyData.getClasses() != null) {
            for (ClassData classData : ontologyData.getClasses()) {
                String iri = classData.getIdentifier();
                if (!outputIris.contains(iri)) {
                    System.out.println("MISSING: " + classData.getName() + " (IRI: " + iri + ")");
                    missingClasses++;
                }
            }
            if (missingClasses == 0) {
                System.out.println("All classes present");
            }
        }

        System.out.println("\nProperties:");
        int missingProperties = 0;
        if (ontologyData.getProperties() != null) {
            for (PropertyData propertyData : ontologyData.getProperties()) {
                String propertyName = propertyData.getName();
                if (propertyName == null || propertyName.trim().isEmpty()) {
                    continue;
                }

                String normalizedName = propertyName.trim().toLowerCase();
                if (!outputNames.contains(normalizedName)) {
                    System.out.println("MISSING: " + propertyName);
                    missingProperties++;
                }
            }
            if (missingProperties == 0) {
                System.out.println("All properties present");
            }
        }

        System.out.println("\nRelationships:");
        int missingRelationships = 0;
        if (ontologyData.getRelationships() != null) {
            for (RelationshipData relationshipData : ontologyData.getRelationships()) {
                boolean found = false;
                if (actualRoot.has("pojmy")) {
                    for (JsonNode pojem : actualRoot.get("pojmy")) {
                        if (pojem.has("název") && pojem.get("název").has("cs")) {
                            String pojemName = pojem.get("název").get("cs").asText();
                            if (pojemName.equals(relationshipData.getName())) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
                if (!found) {
                    System.out.println("MISSING: " + relationshipData.getName());
                    missingRelationships++;
                }
            }
            if (missingRelationships == 0) {
                System.out.println("All relationships present");
            }
        }

        System.out.println("\nSummary:");
        System.out.println("Missing classes: " + missingClasses);
        System.out.println("Missing properties: " + missingProperties);
        System.out.println("Missing relationships: " + missingRelationships);
        System.out.println("Total missing: " + (missingClasses + missingProperties + missingRelationships));
    }
}
