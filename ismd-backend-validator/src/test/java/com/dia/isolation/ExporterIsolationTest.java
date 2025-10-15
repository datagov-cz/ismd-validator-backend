package com.dia.isolation;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.workflow.assertions.OFNAssertions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.ontology.OntModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolation tests for Exporter stage.
 * Validates export logic with pre-built OntModel (not from full pipeline).
 */
@SpringBootTest
@Tag("isolation")
@Tag("exporter")
public class ExporterIsolationTest {

    @Autowired
    private OFNDataTransformer transformer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jsonExporter_withMinimalModel_shouldExportValidJson() throws Exception {
        // Given: Pre-built minimal OntModel
        OntologyData minimalData = createMinimalData();
        TransformationResult transformationResult = transformer.transform(minimalData);

        // When: Export to JSON
        String json = transformer.exportToJson(transformationResult);

        // Then: Verify JSON structure
        OFNAssertions.assertValidOFNJson(json);

        JsonNode root = objectMapper.readTree(json);
        assertNotNull(root.get("@context"), "Should have @context");
        assertNotNull(root.get("iri"), "Should have iri");
        assertNotNull(root.get("typ"), "Should have typ");
    }

    @Test
    void jsonExporter_withCompleteModel_shouldExportAllCharacteristics() throws Exception {
        // Given: Pre-built complete OntModel
        OntologyData completeData = createCompleteData();
        TransformationResult transformationResult = transformer.transform(completeData);

        // When: Export to JSON
        String json = transformer.exportToJson(transformationResult);

        // Then: Verify all characteristics present
        OFNAssertions.assertValidOFNJson(json);

        JsonNode root = objectMapper.readTree(json);
        assertNotNull(root.get("název"), "Should have název");
        assertNotNull(root.get("popis"), "Should have popis");

        // Should have pojmy array
        assertTrue(root.has("pojmy"), "Should have pojmy array");
        assertTrue(root.get("pojmy").isArray(), "pojmy should be array");
        assertTrue(root.get("pojmy").size() > 0, "pojmy should not be empty");
    }

    @Test
    void turtleExporter_withMinimalModel_shouldExportValidTurtle() throws Exception {
        // Given: Pre-built minimal OntModel
        OntologyData minimalData = createMinimalData();
        TransformationResult transformationResult = transformer.transform(minimalData);

        // When: Export to Turtle
        String turtle = transformer.exportToTurtle(transformationResult);

        // Then: Verify Turtle structure
        OFNAssertions.assertValidTurtle(turtle);
        assertTrue(turtle.contains("@prefix") || turtle.contains("PREFIX"),
            "Should contain prefix declarations");
    }

    @Test
    void turtleExporter_withCompleteModel_shouldExportValidTurtle() throws Exception {
        // Given: Pre-built complete OntModel
        OntologyData completeData = createCompleteData();
        TransformationResult transformationResult = transformer.transform(completeData);

        // When: Export to Turtle
        String turtle = transformer.exportToTurtle(transformationResult);

        // Then: Verify Turtle structure
        OFNAssertions.assertValidTurtle(turtle);
        assertFalse(turtle.trim().isEmpty(), "Turtle should not be empty");
    }

    @Test
    void jsonExporter_withXSDDataType_shouldPreserveDataType() throws Exception {
        // Given: Model with XSD data type property
        PropertyData property = new PropertyData();
        property.setName("testXSDProperty");
        property.setDataType("http://www.w3.org/2001/XMLSchema#dateTime");
        property.setIdentifier("test-prop-1");

        OntologyData data = createDataWithProperty(property);
        TransformationResult transformationResult = transformer.transform(data);

        // When: Export to JSON
        String json = transformer.exportToJson(transformationResult);

        // Then: Data type should be preserved in output
        assertTrue(json.contains("dateTime") || json.contains("http://www.w3.org/2001/XMLSchema#dateTime"),
            "XSD dateTime type should be preserved in JSON output");
    }

    @Test
    void jsonExporter_shouldBeIdempotent() throws Exception {
        // Given: Same transformation result
        OntologyData data = createCompleteData();
        TransformationResult transformationResult = transformer.transform(data);

        // When: Export multiple times
        String json1 = transformer.exportToJson(transformationResult);
        String json2 = transformer.exportToJson(transformationResult);

        // Then: Outputs should be identical
        OFNAssertions.assertJsonEquals(json1, json2);
    }

    @Test
    void turtleExporter_shouldBeIdempotent() throws Exception {
        // Given: Same transformation result
        OntologyData data = createCompleteData();
        TransformationResult transformationResult = transformer.transform(data);

        // When: Export multiple times
        String turtle1 = transformer.exportToTurtle(transformationResult);
        String turtle2 = transformer.exportToTurtle(transformationResult);

        // Then: Outputs should be identical (or at least structurally equivalent)
        assertEquals(turtle1, turtle2, "Turtle exports should be identical");
    }

    @Test
    void jsonExporter_withTemporalData_shouldIncludeTemporalInstants() throws Exception {
        // Given: Data with temporal metadata
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Temporal Test");
        metadata.setDateOfCreation("2024-01-01");
        metadata.setDateOfModification("2024-01-02T10:00:00");

        OntologyData data = OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of())
            .properties(List.of())
            .relationships(List.of())
            .hierarchies(List.of())
            .build();

        TransformationResult transformationResult = transformer.transform(data);

        // When: Export to JSON
        String json = transformer.exportToJson(transformationResult);

        // Then: Should include temporal data
        assertTrue(json.contains("časový-okamžik") || json.contains("temporal"),
            "JSON should include temporal instant data");
    }

    // ========== Helper Methods ==========

    private OntologyData createMinimalData() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Minimal Exporter Test");

        ClassData minimalClass = new ClassData();
        minimalClass.setName("MinimalClass");
        minimalClass.setIdentifier("minimal-1");
        minimalClass.setType("Objekt");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of(minimalClass))
            .properties(List.of())
            .relationships(List.of())
            .hierarchies(List.of())
            .build();
    }

    private OntologyData createCompleteData() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Complete Exporter Test");
        metadata.setDescription("Complete test vocabulary for export");
        metadata.setNamespace("https://test.exporter.example.com/");

        ClassData class1 = new ClassData();
        class1.setName("ExporterTestClass");
        class1.setDescription("Test class for exporter");
        class1.setIdentifier("exp-class-1");
        class1.setType("Objekt");

        PropertyData prop1 = new PropertyData();
        prop1.setName("exporterProperty");
        prop1.setDescription("Test property for exporter");
        prop1.setDataType("Řetězec");
        prop1.setDomain("ExporterTestClass");
        prop1.setIdentifier("exp-prop-1");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of(class1))
            .properties(List.of(prop1))
            .relationships(List.of())
            .hierarchies(List.of())
            .build();
    }

    private OntologyData createDataWithProperty(PropertyData property) {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Property Test");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of())
            .properties(List.of(property))
            .relationships(List.of())
            .hierarchies(List.of())
            .build();
    }
}