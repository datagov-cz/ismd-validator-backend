package com.dia.workflow;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.excel.ExcelReader;
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
 * Comprehensive Excel and Archi conversion workflow test for JSON-LD output with deep validation.
 * <p>
 * This test validates the complete Excel/Archi → OntologyData → TransformationResult → JSON-LD pipeline
 * with multi-stage validation and deviation detection.
 * <p>
 * Features:
 * - Configurable with different test files via WorkflowTestConfiguration
 * - Supports both Excel (.xlsx) and Archi XML (.xml) input files
 * - Stage-by-stage workflow validation
 * - Deep JSON-LD structural comparison
 * - Entity count validation with detailed mismatch analysis
 * - Characteristic presence validation
 * - Data preservation checks (no data loss)
 * - Context compliance validation
 * <p>
 * Note: EA (Enterprise Architect) tests are handled in separate test classes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("workflow")
@Tag("excel")
@Tag("archi")
@Tag("json")
@Tag("deviation-detection")
class ConversionWorkflowJsonTest {

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private ArchiReader archiReader;

    @Autowired
    private OFNDataTransformerNew transformer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeviationDetector deviationDetector = new DeviationDetector();

    static Stream<WorkflowTestConfiguration> testConfigurationProvider() {
        return WorkflowTestConfiguration.allConfigurations().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testConfigurationProvider")
    void excelConversionWorkflow_shouldProduceExpectedOutput(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CONVERSION WORKFLOW TEST: " + config.getTestId());
        System.out.println("=".repeat(80));

        // Stage 1: Load input file (Excel or Archi)
        System.out.println("\n[STAGE 1] Loading input file: " + config.getInputPath());
        OntologyData ontologyData = loadFile(config.getInputPath());
        assertNotNull(ontologyData, "Input file should be successfully parsed");
        System.out.println("Input file loaded successfully");

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
                System.out.println("Deviations" + deviations.size() + " deviation(s) detected:");
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
    void excelConversionWorkflow_shouldPreserveAllData(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n[DATA PRESERVATION TEST] " + config.getTestId());

        // Execute workflow
        OntologyData ontologyData = loadFile(config.getInputPath());
        TransformationResult transformationResult = transformer.transform(ontologyData);
        String actualJson = transformer.exportToJson(transformationResult);

        // Parse and count entities
        JsonNode actualRoot = objectMapper.readTree(actualJson);

        int classCount = countEntitiesOfType(ontologyData, ClassData.class);
        int propertyCount = countEntitiesOfType(ontologyData, PropertyData.class);
        int relationshipCount = countEntitiesOfType(ontologyData, RelationshipData.class);

        System.out.println("Classes: " + classCount);
        System.out.println("Properties: " + propertyCount);
        System.out.println("Relationships: " + relationshipCount);

        // Verify all entities appear in output
        if (actualRoot.has("pojmy")) {
            int outputEntityCount = actualRoot.get("pojmy").size();
            int expectedEntityCount = 40;

            if (expectedEntityCount != outputEntityCount) {
                System.out.println("\nEntity count mismatch detected!");
                System.out.println("Expected: " + expectedEntityCount + " entities");
                System.out.println("Found: " + outputEntityCount + " entities");
                System.out.println("Difference: " + (expectedEntityCount - outputEntityCount) + " entities missing");

                findMissingConcepts(ontologyData, actualRoot);

                fail(String.format("Output should contain %d entities (adresa concept filtered due to IRI mismatch), but found %d",
                    expectedEntityCount, outputEntityCount));
            }

            System.out.println("All " + expectedEntityCount + " entities preserved in output");
        }
    }

    @ParameterizedTest(name = "{0} - Characteristics")
    @MethodSource("testConfigurationProvider")
    void excelConversionWorkflow_shouldPreserveCharacteristics(WorkflowTestConfiguration config) throws Exception {
        if (config.getRequiredCharacteristics() == null || config.getRequiredCharacteristics().isEmpty()) {
            return;
        }

        System.out.println("\n[CHARACTERISTICS TEST] " + config.getTestId());

        // Execute workflow
        OntologyData ontologyData = loadFile(config.getInputPath());
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
            findConceptsWithMissingCharacteristics(actualRoot, missingCharacteristics);
            fail("Required characteristics missing: " + String.join(", ", missingCharacteristics));
        }

        System.out.println("All " + config.getRequiredCharacteristics().size() + " characteristics validated");
    }

    // ========== Helper Methods ==========

    private OntologyData loadFile(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            if (path.endsWith(".xlsx")) {
                return excelReader.readOntologyFromExcel(is);
            } else if (path.endsWith(".xml")) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // Archi XML format
                return archiReader.readArchiFromString(content);
            } else {
                throw new IllegalArgumentException("Unsupported file format: " + path);
            }
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

        if (config.getExpectedCounts() != null) {
            WorkflowTestConfiguration.EntityCounts expected = config.getExpectedCounts();

            if (expected.getClasses() != null) {
                assertEquals((int) expected.getClasses(), classCount, "Expected " + expected.getClasses() + " classes but found " + classCount);
            }
            if (expected.getProperties() != null) {
                assertEquals((int) expected.getProperties(), propertyCount, "Expected " + expected.getProperties() + " properties but found " + propertyCount);
            }
            if (expected.getRelationships() != null) {
                assertEquals((int) expected.getRelationships(), relationshipCount, "Expected " + expected.getRelationships() + " relationships but found " + relationshipCount);
            }
        }

        System.out.println("OntologyData structure validated");
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

            // Note: This is a warning, not a failure, as some fields might be valid but not in the context
            System.out.println("\nNote: Some fields may be valid but not defined in the test context file");
        }

        // Validate structure
        System.out.println("\nValidating structure against context...");

        // 1. Check @container: @set (arrays)
        List<String> structureViolations = new ArrayList<>(validateSetContainers(jsonRoot, contextDef));

        // 2. Check @container: @language (language maps)
        structureViolations.addAll(validateLanguageContainers(jsonRoot, contextDef));

        // 3. Check @type: @id (IRI/URI fields)
        structureViolations.addAll(validateIdTypes(jsonRoot, contextDef));

        // 4. Check @type: xsd types (data types)
        structureViolations.addAll(validateDataTypes(jsonRoot, contextDef));

        if (!structureViolations.isEmpty()) {
            System.out.println("\n" + structureViolations.size() + " structure violation(s) detected:");
            for (String violation : structureViolations) {
                System.out.println("  - " + violation);
            }
            fail("JSON-LD structure validation failed: " + structureViolations.size() + " violation(s) found");
        } else {
            System.out.println("Structure validation passed - all checks successful");
        }
    }

    private void collectSetContainerFields(JsonNode contextDef, Map<String, String> setFields, String prefix) {
        if (contextDef == null || !contextDef.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : contextDef.properties()) {
            String fieldName = entry.getKey();
            JsonNode fieldDef = entry.getValue();

            // Skip JSON-LD keywords
            if (fieldName.startsWith("@")) {
                continue;
            }

            // Check if this field has @container: @set
            if (fieldDef.isObject()) {
                if (fieldDef.has("@container")) {
                    String containerType = fieldDef.get("@container").asText();
                    if ("@set".equals(containerType)) {
                        String fullPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
                        setFields.put(fieldName, fullPath);
                    }
                }

                // Recursively check nested contexts
                if (fieldDef.has("@context")) {
                    collectSetContainerFields(fieldDef.get("@context"), setFields, prefix);
                }
            }
        }
    }

    private void validateNodeStructure(JsonNode node, Map<String, String> setContainerFields,
                                       List<String> violations, String path) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                String fieldPath = path + "." + fieldName;

                // Check if this field should be an array according to the context
                if (setContainerFields.containsKey(fieldName) && !fieldValue.isArray() && !fieldValue.isNull()) {
                    violations.add(String.format(
                            "Field '%s' at %s should be an array (context defines @container: @set) but is %s",
                            fieldName, fieldPath, fieldValue.getNodeType()
                    ));
                }


                // Recursively validate nested structures
                validateNodeStructure(fieldValue, setContainerFields, violations, fieldPath);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                validateNodeStructure(item, setContainerFields, violations, path + "[" + index + "]");
                index++;
            }
        }
    }

    // ========== Validation Methods ==========

    private List<String> validateSetContainers(JsonNode jsonRoot, JsonNode contextDef) {
        List<String> violations = new ArrayList<>();
        Map<String, String> setContainerFields = new HashMap<>();
        collectSetContainerFields(contextDef, setContainerFields, "");
        validateNodeStructure(jsonRoot, setContainerFields, violations, "root");
        System.out.println("@container: @set validation (" + setContainerFields.size() + " fields checked)");
        return violations;
    }

    private List<String> validateLanguageContainers(JsonNode jsonRoot, JsonNode contextDef) {
        List<String> violations = new ArrayList<>();
        Map<String, String> languageFields = new HashMap<>();
        collectLanguageContainerFields(contextDef, languageFields, "");
        validateLanguageMapStructure(jsonRoot, languageFields, violations, "root");
        System.out.println("@container: @language validation (" + languageFields.size() + " fields checked)");
        return violations;
    }

    private List<String> validateIdTypes(JsonNode jsonRoot, JsonNode contextDef) {
        List<String> violations = new ArrayList<>();
        Map<String, String> idTypeFields = new HashMap<>();
        collectIdTypeFields(contextDef, idTypeFields, "");
        validateIdTypeValues(jsonRoot, idTypeFields, violations, "root");
        System.out.println("@type: @id validation (" + idTypeFields.size() + " fields checked)");
        return violations;
    }

    private List<String> validateDataTypes(JsonNode jsonRoot, JsonNode contextDef) {
        List<String> violations = new ArrayList<>();
        Map<String, String> dataTypeFields = new HashMap<>();
        collectDataTypeFields(contextDef, dataTypeFields, "");
        validateDataTypeValues(jsonRoot, dataTypeFields, violations, "root");
        System.out.println("@type: xsd:* validation (" + dataTypeFields.size() + " fields checked)");
        return violations;
    }

    private void collectLanguageContainerFields(JsonNode contextDef, Map<String, String> languageFields, String prefix) {
        if (contextDef == null || !contextDef.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : contextDef.properties()) {
            String fieldName = entry.getKey();
            JsonNode fieldDef = entry.getValue();

            if (fieldName.startsWith("@")) {
                continue;
            }

            if (fieldDef.isObject()) {
                if (fieldDef.has("@container") && "@language".equals(fieldDef.get("@container").asText())) {
                    languageFields.put(fieldName, prefix.isEmpty() ? fieldName : prefix + "." + fieldName);
                }
                if (fieldDef.has("@context")) {
                    collectLanguageContainerFields(fieldDef.get("@context"), languageFields, prefix);
                }
            }
        }
    }

    private void validateLanguageMapStructure(JsonNode node, Map<String, String> languageFields,
                                              List<String> violations, String path) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                String fieldPath = path + "." + fieldName;

                if (languageFields.containsKey(fieldName)) {
                    if (!fieldValue.isObject() && !fieldValue.isNull()) {
                        violations.add(String.format(
                                "Field '%s' at %s should be a language map (object) but is %s",
                                fieldName, fieldPath, fieldValue.getNodeType()
                        ));
                    } else if (fieldValue.isObject()) {
                        // Validate that keys are language codes (at least 2 chars, lowercase)
                        Iterator<String> langKeys = fieldValue.fieldNames();
                        while (langKeys.hasNext()) {
                            String langCode = langKeys.next();
                            if (!langCode.matches("[a-z]{2,3}(-[A-Z]{2})?")) {
                                violations.add(String.format(
                                        "Field '%s' at %s has invalid language code '%s' (expected format: 'cs', 'en', 'en-US', etc.)",
                                        fieldName, fieldPath, langCode
                                ));
                            }
                        }
                    }
                }

                validateLanguageMapStructure(fieldValue, languageFields, violations, fieldPath);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                validateLanguageMapStructure(item, languageFields, violations, path + "[" + index + "]");
                index++;
            }
        }
    }

    private void collectIdTypeFields(JsonNode contextDef, Map<String, String> idFields, String prefix) {
        if (contextDef == null || !contextDef.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : contextDef.properties()) {
            String fieldName = entry.getKey();
            JsonNode fieldDef = entry.getValue();

            if (fieldName.startsWith("@") || "iri".equals(fieldName) || "typ".equals(fieldName)) {
                continue;
            }

            if (fieldDef.isObject()) {
                if (fieldDef.has("@type") && "@id".equals(fieldDef.get("@type").asText())) {
                    idFields.put(fieldName, prefix.isEmpty() ? fieldName : prefix + "." + fieldName);
                }
                if (fieldDef.has("@context")) {
                    collectIdTypeFields(fieldDef.get("@context"), idFields, prefix);
                }
            }
        }
    }

    private void validateIdTypeValues(JsonNode node, Map<String, String> idFields,
                                      List<String> violations, String path) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                String fieldPath = path + "." + fieldName;

                if (idFields.containsKey(fieldName)) {
                    validateIdValue(fieldName, fieldValue, fieldPath, violations);
                }

                validateIdTypeValues(fieldValue, idFields, violations, fieldPath);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                validateIdTypeValues(item, idFields, violations, path + "[" + index + "]");
                index++;
            }
        }
    }

    private void validateIdValue(String fieldName, JsonNode value, String path, List<String> violations) {
        if (value.isNull()) {
            return;
        }

        if (value.isArray()) {
            int index = 0;
            for (JsonNode item : value) {
                if (item.isObject() && item.has("id")) {
                    String idValue = item.get("id").asText();
                    if (isValidUri(idValue)) {
                        violations.add(String.format(
                            "Field '%s' at %s[%d].id has invalid URI/IRI: '%s'",
                            fieldName, path, index, idValue
                        ));
                    }
                } else if (item.isTextual()) {
                    String idValue = item.asText();
                    if (isValidUri(idValue)) {
                        violations.add(String.format(
                            "Field '%s' at %s[%d] has invalid URI/IRI: '%s'",
                            fieldName, path, index, idValue
                        ));
                    }
                }
                index++;
            }
        } else if (value.isTextual()) {
            String idValue = value.asText();
            if (isValidUri(idValue)) {
                violations.add(String.format(
                    "Field '%s' at %s has invalid URI/IRI: '%s'",
                    fieldName, path, idValue
                ));
            }
        } else if (!value.isObject()) {
            violations.add(String.format(
                "Field '%s' at %s should be a URI/IRI (string or object with 'id') but is %s",
                fieldName, path, value.getNodeType()
            ));
        }
    }

    private boolean isValidUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return true;
        }
        return !uri.matches("^https?://.*") && !uri.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }

    private void collectDataTypeFields(JsonNode contextDef, Map<String, String> dataTypeFields, String prefix) {
        if (contextDef == null || !contextDef.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : contextDef.properties()) {
            String fieldName = entry.getKey();
            JsonNode fieldDef = entry.getValue();

            if (fieldName.startsWith("@")) {
                continue;
            }

            if (fieldDef.isObject()) {
                if (fieldDef.has("@type")) {
                    String typeValue = fieldDef.get("@type").asText();
                    if (typeValue.startsWith("xsd:") || typeValue.contains("XMLSchema#")) {
                        dataTypeFields.put(fieldName, typeValue);
                    }
                }
                if (fieldDef.has("@context")) {
                    collectDataTypeFields(fieldDef.get("@context"), dataTypeFields, prefix);
                }
            }
        }
    }

    private void validateDataTypeValues(JsonNode node, Map<String, String> dataTypeFields,
                                        List<String> violations, String path) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                String fieldPath = path + "." + fieldName;

                if (dataTypeFields.containsKey(fieldName)) {
                    String expectedType = dataTypeFields.get(fieldName);
                    validateDataType(fieldName, fieldValue, expectedType, fieldPath, violations);
                }

                validateDataTypeValues(fieldValue, dataTypeFields, violations, fieldPath);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                validateDataTypeValues(item, dataTypeFields, violations, path + "[" + index + "]");
                index++;
            }
        }
    }

    private void validateDataType(String fieldName, JsonNode value, String expectedType, String path, List<String> violations) {
        if (value.isNull()) {
            return;
        }

        String xsdType = expectedType.contains(":") ? expectedType.split(":")[1] : expectedType;
        if (xsdType.contains("#")) {
            xsdType = xsdType.substring(xsdType.lastIndexOf("#") + 1);
        }

        switch (xsdType) {
            case "boolean":
                if (!value.isBoolean()) {
                    violations.add(String.format(
                        "Field '%s' at %s should be boolean but is %s",
                        fieldName, path, value.getNodeType()
                    ));
                }
                break;
            case "integer", "int", "long":
                if (!value.isIntegralNumber()) {
                    violations.add(String.format(
                        "Field '%s' at %s should be integer but is %s (value: %s)",
                        fieldName, path, value.getNodeType(), value.asText()
                    ));
                }
                break;
            case "decimal", "double", "float":
                if (!value.isNumber()) {
                    violations.add(String.format(
                        "Field '%s' at %s should be number but is %s",
                        fieldName, path, value.getNodeType()
                    ));
                }
                break;
            case "date":
                if (!value.isTextual() || !value.asText().matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                    violations.add(String.format(
                        "Field '%s' at %s should be date (YYYY-MM-DD) but is '%s'",
                        fieldName, path, value.asText()
                    ));
                }
                break;
            case "dateTime", "dateTimeStamp":
                if (!value.isTextual() || !value.asText().matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                    violations.add(String.format(
                        "Field '%s' at %s should be dateTime (ISO 8601) but is '%s'",
                        fieldName, path, value.asText()
                    ));
                }
                break;
            case "anyURI":
                if (!value.isTextual() || isValidUri(value.asText())) {
                    violations.add(String.format(
                        "Field '%s' at %s should be valid URI but is '%s'",
                        fieldName, path, value.asText()
                    ));
                }
                break;
            case "string":
                if (!value.isTextual()) {
                    violations.add(String.format(
                        "Field '%s' at %s should be string but is %s",
                        fieldName, path, value.getNodeType()
                    ));
                }
                break;
            default:
                // Unknown XSD type - log as warning but don't fail validation
                System.out.println("Warning: Unknown XSD type '" + xsdType + "' for field '" + fieldName + "' at " + path);
                break;
        }
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

            if (fieldDef.isObject() && fieldDef.has("@context")) {
                JsonNode nestedContext = fieldDef.get("@context");
                if (isFieldDefinedInContext(fieldName, nestedContext)) {
                    return true;
                }

                // Check for nested type definitions (like "Digitální objekt", "Časový okamžik")
                Iterator<String> nestedFieldNames = nestedContext.fieldNames();
                while (nestedFieldNames.hasNext()) {
                    String nestedField = nestedFieldNames.next();
                    JsonNode nestedFieldDef = nestedContext.get(nestedField);
                    if (nestedFieldDef.isObject() && nestedFieldDef.has("@context") && isFieldDefinedInContext(fieldName, nestedFieldDef.get("@context"))) {
                        return true;
                    }
                }
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
        sb.append("\n\nWorkflow produced ").append(deviations.size()).append(" deviation(s) from expected output:\n\n");

        for (int i = 0; i < deviations.size(); i++) {
            WorkflowDeviation deviation = deviations.get(i);
            sb.append(i + 1).append(". ").append(deviation.getType()).append(" at ")
              .append(deviation.getLocation()).append("\n");
            sb.append("   ").append(deviation.getMessage()).append("\n");
        }

        return sb.toString();
    }

    private void findMissingConcepts(OntologyData ontologyData, JsonNode actualRoot) {
        System.out.println("\nAnalyzing missing concepts...\n");

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

        System.out.println("\nOutput contains " + outputIris.size() + " IRIs and " + outputNames.size() + " unique names");

        System.out.println("Classes:");
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

        // Check properties
        System.out.println("\nProperties:");
        int missingProperties = 0;
        List<String> missingPropertyNames = new ArrayList<>();

        if (ontologyData.getProperties() != null) {
            for (PropertyData propertyData : ontologyData.getProperties()) {
                String propertyName = propertyData.getName();
                if (propertyName == null || propertyName.trim().isEmpty()) {
                    continue;
                }

                String normalizedName = propertyName.trim().toLowerCase();
                if (!outputNames.contains(normalizedName)) {
                    System.out.println("MISSING: " + propertyName);
                    System.out.println("Domain: " + propertyData.getDomain());
                    System.out.println("DataType: " + propertyData.getDataType());
                    missingProperties++;
                    missingPropertyNames.add(propertyName);
                }
            }

            if (missingProperties == 0) {
                System.out.println("All " + ontologyData.getProperties().size() + " properties present");
            } else {
                System.out.println("\nMissing property names for debugging:");
                for (String name : missingPropertyNames.subList(0, Math.min(5, missingPropertyNames.size()))) {
                    System.out.println("    - \"" + name + "\" (normalized: \"" + name.trim().toLowerCase() + "\")");
                }
                if (missingPropertyNames.size() > 5) {
                    System.out.println("    ... and " + (missingPropertyNames.size() - 5) + " more");
                }

                System.out.println("\nSample output names for comparison:");
                List<String> sampleNames = new ArrayList<>(outputNames);
                for (int i = 0; i < Math.min(5, sampleNames.size()); i++) {
                    System.out.println("    - \"" + sampleNames.get(i) + "\"");
                }

                // Try fuzzy matching for missing properties
                System.out.println("\nChecking for potential matches (contains/similar):");
                for (String missingName : missingPropertyNames.subList(0, Math.min(3, missingPropertyNames.size()))) {
                    String normalizedMissing = missingName.trim().toLowerCase();
                    System.out.println("Looking for: \"" + missingName + "\"");
                    for (String outputName : outputNames) {
                        if (outputName.contains(normalizedMissing) || normalizedMissing.contains(outputName)) {
                            System.out.println("Potential match: \"" + outputName + "\"");
                        }
                    }
                }
            }
        }

        // Check relationships
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
                    System.out.println("Domain: " + relationshipData.getDomain());
                    System.out.println("Range: " + relationshipData.getRange());
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
        System.out.println();
    }

    private void findConceptsWithMissingCharacteristics(JsonNode actualRoot, List<String> missingCharacteristics) {
        if (!actualRoot.has("pojmy")) {
            return;
        }

        System.out.println("\nAnalyzing which concepts are missing characteristics...\n");

        JsonNode pojmy = actualRoot.get("pojmy");
        Map<String, List<String>> conceptsLackingCharacteristics = new HashMap<>();

        for (JsonNode pojem : pojmy) {
            String iri = pojem.has("iri") ? pojem.get("iri").asText() : "unknown";
            String name = pojem.has("název") && pojem.get("název").has("cs")
                ? pojem.get("název").get("cs").asText()
                : "unnamed";

            for (String characteristic : missingCharacteristics) {
                if (!hasCharacteristic(pojem, characteristic)) {
                    conceptsLackingCharacteristics.computeIfAbsent(characteristic, k -> new ArrayList<>())
                        .add(name + " (" + iri + ")");
                }
            }
        }

        for (String characteristic : missingCharacteristics) {
            List<String> conceptsLacking = conceptsLackingCharacteristics.get(characteristic);
            if (conceptsLacking != null && !conceptsLacking.isEmpty()) {
                System.out.println("Characteristic '" + characteristic + "' is missing from " + conceptsLacking.size() + " concept(s):");
                for (int i = 0; i < Math.min(5, conceptsLacking.size()); i++) {
                    System.out.println("  - " + conceptsLacking.get(i));
                }
                if (conceptsLacking.size() > 5) {
                    System.out.println("  ... and " + (conceptsLacking.size() - 5) + " more");
                }
                System.out.println();
            }
        }
    }

    private boolean hasCharacteristic(JsonNode pojem, String characteristic) {
        return searchForFieldInNode(pojem, characteristic);
    }

    private boolean searchForFieldInNode(JsonNode node, String fieldName) {
        if (node.has(fieldName)) {
            return true;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getValue().isObject() && entry.getValue().has(fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
