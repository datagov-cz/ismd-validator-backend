package com.dia.workflow.assertions;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.workflow.config.TestOntologyMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Custom assertion library for OFN validation.
 * Provides content-independent assertions that work with test metadata.
 */
public class OFNAssertions {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ========== JSON Assertions ==========

    public static void assertValidOFNJson(String json) throws Exception {
        assertNotNull(json, "JSON output should not be null");
        assertFalse(json.isEmpty(), "JSON output should not be empty");

        JsonNode root = objectMapper.readTree(json);

        assertNotNull(root.get("@context"), "JSON should have @context");
        assertNotNull(root.get("iri"), "JSON should have iri");
        assertNotNull(root.get("typ"), "JSON should have typ");
        assertTrue(root.get("typ").isArray(), "typ should be an array");
    }

    public static void assertJsonMatchesMetadata(String json, TestOntologyMetadata metadata) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        if (metadata.getVocabularyExpectations() != null) {
            var vocabExp = metadata.getVocabularyExpectations();

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveName())) {
                assertNotNull(root.get("název"), "JSON should have název");
            }
            if (Boolean.TRUE.equals(vocabExp.getShouldHaveDescription())) {
                assertNotNull(root.get("popis"), "JSON should have popis");
            }
        }

        if (metadata.getRequiredCharacteristics() != null) {
            for (String characteristic : metadata.getRequiredCharacteristics()) {
                assertCharacteristicPresent(root, characteristic);
            }
        }

        if (root.has("pojmy") && metadata.getExpectedCounts() != null) {
            JsonNode pojmy = root.get("pojmy");
            if (pojmy.isArray()) {
                assertEntityCounts(pojmy, metadata.getExpectedCounts());
            }
        }
    }

    private static void assertCharacteristicPresent(JsonNode root, String characteristic) {
        boolean found = searchForField(root, characteristic);
        assertTrue(found, "Required characteristic '" + characteristic + "' not found in JSON");
    }

    private static boolean searchForField(JsonNode node, String fieldName) {
        if (node.has(fieldName)) {
            return true;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
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

    private static void assertEntityCounts(JsonNode pojmy, TestOntologyMetadata.EntityCounts expectedCounts) {
        int classCount = 0;
        int propertyCount = 0;
        int relationshipCount = 0;

        for (JsonNode pojem : pojmy) {
            if (pojem.has("typ")) {
                JsonNode typ = pojem.get("typ");
                String typString = typ.isArray() && typ.size() > 0 ? typ.get(0).asText() : typ.asText();

                if (typString.contains("třída") || typString.contains("class")) {
                    classCount++;
                } else if (typString.contains("vlastnost") || typString.contains("property")) {
                    propertyCount++;
                } else if (typString.contains("vztah") || typString.contains("relationship")) {
                    relationshipCount++;
                }
            }
        }

        if (expectedCounts.getClasses() != null) {
            assertTrue(expectedCounts.getClasses().matches(classCount),
                String.format("Class count %d does not match expectations", classCount));
        }
        if (expectedCounts.getProperties() != null) {
            assertTrue(expectedCounts.getProperties().matches(propertyCount),
                String.format("Property count %d does not match expectations", propertyCount));
        }
        if (expectedCounts.getRelationships() != null) {
            assertTrue(expectedCounts.getRelationships().matches(relationshipCount),
                String.format("Relationship count %d does not match expectations", relationshipCount));
        }
    }

    public static void assertJsonEquals(String expectedJson, String actualJson) throws Exception {
        JsonNode expected = objectMapper.readTree(expectedJson);
        JsonNode actual = objectMapper.readTree(actualJson);
        assertEquals(expected, actual, "JSON outputs should be semantically equal");
    }

    // ========== OntologyData Assertions ==========

    public static void assertOntologyDataMatchesMetadata(OntologyData data, TestOntologyMetadata metadata) {
        assertNotNull(data, "OntologyData should not be null");

        if (metadata.getVocabularyExpectations() != null) {
            var vocabExp = metadata.getVocabularyExpectations();
            var vocabData = data.getVocabularyMetadata();

            if (Boolean.TRUE.equals(vocabExp.getShouldHaveName())) {
                assertNotNull(vocabData, "VocabularyMetadata should not be null");
                assertNotNull(vocabData.getName(), "Vocabulary name should not be null");
                assertFalse(vocabData.getName().isEmpty(), "Vocabulary name should not be empty");
            }
            if (Boolean.TRUE.equals(vocabExp.getShouldHaveDescription())) {
                assertNotNull(vocabData, "VocabularyMetadata should not be null");
                assertNotNull(vocabData.getDescription(), "Vocabulary description should not be null");
            }
            if (Boolean.TRUE.equals(vocabExp.getShouldHaveNamespace())) {
                assertNotNull(vocabData, "VocabularyMetadata should not be null");
                assertNotNull(vocabData.getNamespace(), "Vocabulary namespace should not be null");
            }
        }

        if (metadata.getExpectedCounts() != null) {
            var counts = metadata.getExpectedCounts();

            if (counts.getClasses() != null) {
                int actualCount = data.getClasses() != null ? data.getClasses().size() : 0;
                assertTrue(counts.getClasses().matches(actualCount),
                    String.format("Class count %d does not match expectations", actualCount));
            }
            if (counts.getProperties() != null) {
                int actualCount = data.getProperties() != null ? data.getProperties().size() : 0;
                assertTrue(counts.getProperties().matches(actualCount),
                    String.format("Property count %d does not match expectations", actualCount));
            }
            if (counts.getRelationships() != null) {
                int actualCount = data.getRelationships() != null ? data.getRelationships().size() : 0;
                assertTrue(counts.getRelationships().matches(actualCount),
                    String.format("Relationship count %d does not match expectations", actualCount));
            }
            if (counts.getHierarchies() != null) {
                int actualCount = data.getHierarchies() != null ? data.getHierarchies().size() : 0;
                assertTrue(counts.getHierarchies().matches(actualCount),
                    String.format("Hierarchy count %d does not match expectations", actualCount));
            }
        }
    }

    public static void assertNoDataLoss(OntologyData data) {
        assertNotNull(data, "OntologyData should not be null");
        assertNotNull(data.getVocabularyMetadata(), "VocabularyMetadata should not be null");
        assertNotNull(data.getClasses(), "Classes list should not be null");
        assertNotNull(data.getProperties(), "Properties list should not be null");
        assertNotNull(data.getRelationships(), "Relationships list should not be null");
        assertNotNull(data.getHierarchies(), "Hierarchies list should not be null");
    }

    // ========== TransformationResult Assertions ==========

    public static void assertValidTransformationResult(TransformationResult result) {
        assertNotNull(result, "TransformationResult should not be null");
        assertNotNull(result.getOntModel(), "OntModel should not be null");
        assertNotNull(result.getResourceMap(), "ResourceMap should not be null");
        assertNotNull(result.getModelProperties(), "ModelProperties should not be null");
        assertNotNull(result.getEffectiveNamespace(), "EffectiveNamespace should not be null");

        assertTrue(result.getResourceMap().containsKey("ontology"),
            "ResourceMap should contain 'ontology' resource");
    }

    public static void assertTransformationResultMatchesMetadata(
            TransformationResult result,
            TestOntologyMetadata metadata) {

        assertValidTransformationResult(result);

        OntModel model = result.getOntModel();
        assertFalse(model.isEmpty(), "OntModel should not be empty");

        if (metadata.getExpectedCounts() != null) {
            int expectedMinResources = calculateMinimumExpectedResources(metadata.getExpectedCounts());
            assertTrue(result.getResourceMap().size() >= expectedMinResources,
                String.format("ResourceMap should have at least %d resources, but has %d",
                    expectedMinResources, result.getResourceMap().size()));
        }
    }

    private static int calculateMinimumExpectedResources(TestOntologyMetadata.EntityCounts counts) {
        int total = 1;

        if (counts.getClasses() != null && counts.getClasses().getMinimum() != null) {
            total += counts.getClasses().getMinimum();
        }
        if (counts.getProperties() != null && counts.getProperties().getMinimum() != null) {
            total += counts.getProperties().getMinimum();
        }
        if (counts.getRelationships() != null && counts.getRelationships().getMinimum() != null) {
            total += counts.getRelationships().getMinimum();
        }

        return total;
    }

    // ========== RDF/Turtle Assertions ==========

    public static void assertValidTurtle(String turtle) {
        assertNotNull(turtle, "Turtle output should not be null");
        assertFalse(turtle.isEmpty(), "Turtle output should not be empty");
        assertTrue(turtle.contains("@prefix") || turtle.contains("PREFIX"),
            "Turtle should contain prefix declarations");
    }

    public static void assertPropertyHasXSDRange(
            TransformationResult result,
            String propertyName,
            String expectedXSDType) {

        Resource property = result.getResourceMap().get(propertyName);
        assertNotNull(property, "Property '" + propertyName + "' should exist in resource map");

        Statement rangeStmt = property.getProperty(RDFS.range);
        assertNotNull(rangeStmt, "Property '" + propertyName + "' should have rdfs:range");

        String actualRange = rangeStmt.getResource().getURI();
        assertEquals(expectedXSDType, actualRange,
            String.format("Property '%s' should have range %s but has %s",
                propertyName, expectedXSDType, actualRange));
    }

    public static void assertClassExists(TransformationResult result, String className) {
        Resource classResource = result.getResourceMap().get(className);
        assertNotNull(classResource, "Class '" + className + "' should exist in resource map");

        OntModel model = result.getOntModel();
        assertNotNull(model.getOntClass(classResource.getURI()), "Resource '" + className + "' should be an OWL/RDFS class");
    }

    public static void assertTemporalMetadataExists(TransformationResult result) {
        OntModel model = result.getOntModel();

        boolean hasTemporalInstants = model.listSubjects().toList().stream()
            .anyMatch(resource -> resource.getURI() != null &&
                resource.getURI().contains("časový-okamžik"));

        assertTrue(hasTemporalInstants, "Model should contain temporal instant resources");
    }

    // ========== Content Comparison Assertions ==========

    public static void assertCrossFormatConsistency(
            List<String> jsonOutputs,
            TestOntologyMetadata metadata) throws Exception {

        assertTrue(jsonOutputs.size() > 1,
            "Need at least 2 outputs to compare cross-format consistency");

        for (String json : jsonOutputs) {
            assertValidOFNJson(json);
            assertJsonMatchesMetadata(json, metadata);
        }

        JsonNode firstOutput = objectMapper.readTree(jsonOutputs.get(0));
        for (int i = 1; i < jsonOutputs.size(); i++) {
            JsonNode otherOutput = objectMapper.readTree(jsonOutputs.get(i));
            assertSameEntityCounts(firstOutput, otherOutput, "Output " + i);
        }
    }

    private static void assertSameEntityCounts(JsonNode expected, JsonNode actual, String label) {
        if (expected.has("pojmy") && actual.has("pojmy")) {
            assertEquals(expected.get("pojmy").size(), actual.get("pojmy").size(),
                label + " should have same number of pojmy");
        }
    }
}