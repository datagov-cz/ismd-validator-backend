package com.dia.transformer;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import com.dia.exporter.JsonExporter;
import com.dia.exporter.TurtleExporter;
import org.apache.jena.ontology.OntModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class OFNDataTransformerMainWorkflowTest {

    private OFNDataTransformer transformer;
    private OntologyData validOntologyData;
    private VocabularyMetadata vocabularyMetadata;

    @BeforeEach
    void setUp() {
        transformer = new OFNDataTransformer();

        vocabularyMetadata = new VocabularyMetadata();
        vocabularyMetadata.setName("Test Vocabulary");
        vocabularyMetadata.setDescription("Test Description");
        vocabularyMetadata.setNamespace("https://test.example.com/");
        vocabularyMetadata.setDateOfCreation("2024-01-01");
        vocabularyMetadata.setDateOfModification("2024-01-02T10:30:00");

        validOntologyData = createValidOntologyData();
    }

    @Test
    void transform_WithValidData_ShouldReturnTransformationResult() throws ConversionException {
        // When
        TransformationResult result = transformer.transform(validOntologyData);

        // Then
        assertNotNull(result);
        assertNotNull(result.getOntModel());
        assertNotNull(result.getResourceMap());
        assertNotNull(result.getModelProperties());
        assertEquals("Test Vocabulary", result.getModelName());
        assertNotNull(result.getEffectiveNamespace());

        assertTrue(result.getResourceMap().containsKey("ontology"));
        assertFalse(false);
    }

    @Test
    void transform_WithComplexData_ShouldProcessAllEntities() throws ConversionException {
        // Given
        OntologyData complexData = OntologyData.builder()
                .vocabularyMetadata(vocabularyMetadata)
                .classes(createTestClasses())
                .properties(createTestProperties())
                .relationships(createTestRelationships())
                .hierarchies(createTestHierarchies())
                .build();

        // When
        TransformationResult result = transformer.transform(complexData);

        // Then
        assertNotNull(result);
        assertTrue(result.getResourceMap().size() >= 6); // ontology + 2 classes + 2 properties + 1 relationship

        assertTrue(result.getResourceMap().containsKey("Person"));
        assertTrue(result.getResourceMap().containsKey("Organization"));
        assertTrue(result.getResourceMap().containsKey("name"));
        assertTrue(result.getResourceMap().containsKey("email"));
    }

    @Test
    void transform_WithTemporalMetadata_ShouldAddTemporalInstants() throws ConversionException {
        // When
        TransformationResult result = transformer.transform(validOntologyData);

        // Then
        assertNotNull(result);
        OntModel model = result.getOntModel();

        boolean hasTemporalInstants = model.listSubjects().toList().stream()
                .anyMatch(resource -> resource.getURI() != null &&
                        resource.getURI().contains("časový-okamžik"));

        assertTrue(hasTemporalInstants, "Should contain temporal instant resources");
    }

    @Test
    void exportToJson_WithValidTransformationResult_ShouldReturnJsonString() {
        // Given
        TransformationResult transformationResult = transformer.transform(validOntologyData);
        String expectedJson = "{\"vocabulary\": \"test\"}";

        try (MockedConstruction<JsonExporter> mockedExporter = mockConstruction(JsonExporter.class,
                (mock, context) -> when(mock.exportToJson()).thenReturn(expectedJson))) {

            // When
            String result = transformer.exportToJson(transformationResult);

            // Then
            assertEquals(expectedJson, result);
            assertEquals(1, mockedExporter.constructed().size());
        }
    }

    @Test
    void exportToTurtle_WithValidTransformationResult_ShouldReturnTurtleString() {
        // Given
        TransformationResult transformationResult = transformer.transform(validOntologyData);
        String expectedTurtle = "@prefix ex: <http://example.org/> .";

        try (MockedConstruction<TurtleExporter> mockedExporter = mockConstruction(TurtleExporter.class,
                (mock, context) -> when(mock.exportToTurtle()).thenReturn(expectedTurtle))) {

            // When
            String result = transformer.exportToTurtle(transformationResult);

            // Then
            assertEquals(expectedTurtle, result);
            assertEquals(1, mockedExporter.constructed().size());
        }
    }

    @Test
    void exportToJson_WhenExporterThrowsException_ShouldThrowJsonExportException() {
        // Given
        TransformationResult transformationResult = transformer.transform(validOntologyData);
        MDC.put("requestId", "test-123");

        try (MockedConstruction<JsonExporter> mockedExporter = mockConstruction(JsonExporter.class,
                (mock, context) -> when(mock.exportToJson()).thenThrow(new RuntimeException("Export failed")))) {

            // When & Then
            JsonExportException exception = assertThrows(JsonExportException.class,
                    () -> transformer.exportToJson(transformationResult));

            assertTrue(exception.getMessage().contains("Neočekávaná chyba při exportu do JSON"));
            assertNotNull(exception.getCause());
        } finally {
            MDC.clear();
        }
    }

    @Test
    void exportToTurtle_WhenExporterThrowsException_ShouldThrowTurtleExportException() {
        // Given
        TransformationResult transformationResult = transformer.transform(validOntologyData);
        MDC.put("requestId", "test-456");

        try (MockedConstruction<TurtleExporter> mockedExporter = mockConstruction(TurtleExporter.class,
                (mock, context) -> when(mock.exportToTurtle()).thenThrow(new RuntimeException("Export failed")))) {

            // When & Then
            TurtleExportException exception = assertThrows(TurtleExportException.class,
                    () -> transformer.exportToTurtle(transformationResult));

            assertTrue(exception.getMessage().contains("Neočekávaná chyba při exportu do Turtle"));
            assertNotNull(exception.getCause());
        } finally {
            MDC.clear();
        }
    }

    @Test
    void transform_WithMinimalValidData_ShouldSucceed() throws ConversionException {
        // Given
        VocabularyMetadata minimalMetadata = new VocabularyMetadata();
        minimalMetadata.setName("Minimal Vocab");

        OntologyData minimalData = OntologyData.builder()
                .vocabularyMetadata(minimalMetadata)
                .classes(List.of())
                .properties(List.of())
                .relationships(List.of())
                .hierarchies(List.of())
                .build();

        // When
        TransformationResult result = transformer.transform(minimalData);

        // Then
        assertNotNull(result);
        assertEquals("Minimal Vocab", result.getModelName());
        assertTrue(result.getResourceMap().containsKey("ontology"));
    }

    @Test
    void transform_EndToEndWorkflow_ShouldProcessCorrectly() throws ConversionException {
        // Given
        OntologyData comprehensiveData = createComprehensiveOntologyData();

        // When
        TransformationResult result = transformer.transform(comprehensiveData);

        // Then
        assertNotNull(result);

        // Verify ontology resource
        assertTrue(result.getResourceMap().containsKey("ontology"));

        // Verify model properties
        Map<String, String> modelProps = result.getModelProperties();
        assertEquals("Comprehensive Test", modelProps.get("název"));
        assertEquals("Comprehensive test vocabulary", modelProps.get("popis"));

        // Verify namespace handling
        assertTrue(result.getEffectiveNamespace().endsWith("/") || result.getEffectiveNamespace().endsWith("#"));

        // Verify model structure
        OntModel model = result.getOntModel();
        assertNotNull(model);
        assertFalse(model.isEmpty());
    }

    // Helper methods

    private OntologyData createValidOntologyData() {
        return OntologyData.builder()
                .vocabularyMetadata(vocabularyMetadata)
                .classes(createTestClasses())
                .properties(createTestProperties())
                .relationships(List.of())
                .hierarchies(List.of())
                .build();
    }

    private List<ClassData> createTestClasses() {
        ClassData person = new ClassData();
        person.setName("Person");
        person.setDescription("A human being");
        person.setType("Subjekt práva");
        person.setIdentifier("person-1");

        ClassData organization = new ClassData();
        organization.setName("Organization");
        organization.setDescription("A legal entity");
        organization.setType("Objekt práva");
        organization.setIdentifier("org-1");

        return List.of(person, organization);
    }

    private List<PropertyData> createTestProperties() {
        PropertyData name = new PropertyData();
        name.setName("name");
        name.setDescription("The name of something");
        name.setDataType("Řetězec");
        name.setDomain("Person");
        name.setIdentifier("name-1");

        PropertyData email = new PropertyData();
        email.setName("email");
        email.setDescription("Email address");
        email.setDataType("URI, IRI, URL");
        email.setDomain("Person");
        email.setIdentifier("email-1");

        return List.of(name, email);
    }

    private List<RelationshipData> createTestRelationships() {
        RelationshipData memberOf = new RelationshipData();
        memberOf.setName("memberOf");
        memberOf.setDescription("Person is member of organization");
        memberOf.setDomain("Person");
        memberOf.setRange("Organization");
        memberOf.setIdentifier("member-1");

        return List.of(memberOf);
    }

    private List<HierarchyData> createTestHierarchies() {
        HierarchyData hierarchy = new HierarchyData();
        hierarchy.setSubClass("Person");
        hierarchy.setSuperClass("Agent");
        hierarchy.setRelationshipName("is-a");
        hierarchy.setDescription("Person is a type of Agent");

        return List.of(hierarchy);
    }

    private OntologyData createComprehensiveOntologyData() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Comprehensive Test");
        metadata.setDescription("Comprehensive test vocabulary");
        metadata.setNamespace("https://comprehensive.test.com/");
        metadata.setDateOfCreation("2024-01-01");
        metadata.setDateOfModification("2024-01-02T15:30:00Z");

        return OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(createTestClasses())
                .properties(createTestProperties())
                .relationships(createTestRelationships())
                .hierarchies(createTestHierarchies())
                .build();
    }
}
