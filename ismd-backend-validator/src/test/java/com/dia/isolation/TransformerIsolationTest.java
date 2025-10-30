package com.dia.isolation;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.workflow.assertions.OFNAssertions;
import com.dia.workflow.config.TestConfiguration;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolation tests for Transformer stage.
 * Validates transformation logic with synthetic OntologyData (not from readers).
 */
@SpringBootTest
@Tag("isolation")
@Tag("transformer")
public class TransformerIsolationTest {

    @Autowired
    private OFNDataTransformer transformer;

    /**
     * Provides test configurations
     */
    static Stream<TestConfiguration> testConfigurationProvider() {
        return TestConfiguration.allStandardConfigurations().stream();
    }

    @Test
    void transformer_withSyntheticMinimalData_shouldCreateValidModel() throws Exception {
        // Given: Synthetic minimal OntologyData
        OntologyData syntheticData = createSyntheticMinimalData();

        // When: Transform
        TransformationResult result = transformer.transform(syntheticData);

        // Then: Verify RDF model structure
        OFNAssertions.assertValidTransformationResult(result);
        assertNotNull(result.getOntModel(), "OntModel should be created");
        assertFalse(result.getOntModel().isEmpty(), "OntModel should not be empty");
        assertTrue(result.getResourceMap().containsKey("ontology"),
            "ResourceMap should contain ontology resource");
    }

    @Test
    void transformer_withSyntheticCompleteData_shouldCreateAllResources() throws Exception {
        // Given: Synthetic complete OntologyData
        OntologyData syntheticData = createSyntheticCompleteData();

        // When: Transform
        TransformationResult result = transformer.transform(syntheticData);

        // Then: Verify all resources created
        OFNAssertions.assertValidTransformationResult(result);

        // Classes should be in resource map
        assertTrue(result.getResourceMap().containsKey("TestClass1"),
            "Class TestClass1 should be in resource map");
        assertTrue(result.getResourceMap().containsKey("TestClass2"),
            "Class TestClass2 should be in resource map");

        // Properties should be in resource map
        assertTrue(result.getResourceMap().containsKey("testProperty1"),
            "Property testProperty1 should be in resource map");
        assertTrue(result.getResourceMap().containsKey("testProperty2"),
            "Property testProperty2 should be in resource map");
    }

    @Test
    @Tag("regression")
    void transformer_withXSDDataType_shouldSetCorrectRange() throws Exception {
        // Regression test for Issue #109
        // Given: Synthetic property with XSD gDay data type
        PropertyData property = new PropertyData();
        property.setName("testXSDProperty");
        property.setDescription("Test property with XSD gDay");
        property.setDataType("http://www.w3.org/2001/XMLSchema#gDay");
        property.setIdentifier("test-xsd-prop-1");

        OntologyData data = createSyntheticDataWithProperty(property);

        // When: Transform
        TransformationResult result = transformer.transform(data);

        // Then: Range should be XSD type, not rdfs:Literal
        Resource propertyResource = result.getResourceMap().get("testXSDProperty");
        assertNotNull(propertyResource, "Property should exist in resource map");

        Statement rangeStmt = propertyResource.getProperty(RDFS.range);
        assertNotNull(rangeStmt, "Property should have rdfs:range");

        String actualRange = rangeStmt.getResource().getURI();
        assertEquals("http://www.w3.org/2001/XMLSchema#gDay", actualRange,
            "XSD gDay should be preserved as range, not converted to rdfs:Literal");
    }

    @Test
    void transformer_withCzechDataType_shouldMapToCorrectXSD() throws Exception {
        // Given: Synthetic property with Czech data type
        PropertyData property = new PropertyData();
        property.setName("testCzechProperty");
        property.setDescription("Test property with Czech type");
        property.setDataType("Řetězec"); // Czech type for String
        property.setIdentifier("test-czech-prop-1");

        OntologyData data = createSyntheticDataWithProperty(property);

        // When: Transform
        TransformationResult result = transformer.transform(data);

        // Then: Should map to XSD string
        Resource propertyResource = result.getResourceMap().get("testCzechProperty");
        assertNotNull(propertyResource, "Property should exist in resource map");

        Statement rangeStmt = propertyResource.getProperty(RDFS.range);
        assertNotNull(rangeStmt, "Property should have rdfs:range");

        String actualRange = rangeStmt.getResource().getURI();
        assertEquals("http://www.w3.org/2001/XMLSchema#string", actualRange,
            "Czech type 'Řetězec' should map to xsd:string");
    }

    @Test
    void transformer_withDomainAndRange_shouldCreateObjectProperty() throws Exception {
        // Given: Relationship with domain and range
        RelationshipData relationship = new RelationshipData();
        relationship.setName("testRelationship");
        relationship.setDescription("Test relationship");
        relationship.setDomain("TestClass1");
        relationship.setRange("TestClass2");
        relationship.setIdentifier("test-rel-1");

        OntologyData data = createSyntheticDataWithRelationship(relationship);

        // When: Transform
        TransformationResult result = transformer.transform(data);

        // Then: Should create object property with correct domain and range
        Resource relResource = result.getResourceMap().get("testRelationship");
        assertNotNull(relResource, "Relationship should exist in resource map");

        // Verify it's an object property (range is a class, not literal)
        Statement rangeStmt = relResource.getProperty(RDFS.range);
        assertNotNull(rangeStmt, "Relationship should have rdfs:range");
    }

    @ParameterizedTest
    @MethodSource("testConfigurationProvider")
    void transformer_shouldBeIdempotent(TestConfiguration config) throws Exception {
        // Given: Synthetic data
        OntologyData data = createSyntheticCompleteData();

        // When: Transform twice
        TransformationResult result1 = transformer.transform(data);
        TransformationResult result2 = transformer.transform(data);

        // Then: Results should be structurally identical
        assertEquals(result1.getResourceMap().size(), result2.getResourceMap().size(),
            "Transformer should produce identical results");
        assertEquals(result1.getModelName(), result2.getModelName(),
            "Model names should be identical");
    }

    // ========== Helper Methods - Synthetic Data Builders ==========

    private OntologyData createSyntheticMinimalData() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Synthetic Minimal Vocabulary");

        ClassData minimalClass = new ClassData();
        minimalClass.setName("MinimalClass");
        minimalClass.setIdentifier("minimal-class-1");
        minimalClass.setType("Objekt");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of(minimalClass))
            .properties(List.of())
            .relationships(List.of())
            .hierarchies(List.of())
            .build();
    }

    private OntologyData createSyntheticCompleteData() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Synthetic Complete Vocabulary");
        metadata.setDescription("A complete synthetic vocabulary for testing");
        metadata.setNamespace("https://test.synthetic.example.com/");
        metadata.setDateOfCreation("2024-01-01");
        metadata.setDateOfModification("2024-01-02T10:00:00");

        ClassData class1 = new ClassData();
        class1.setName("TestClass1");
        class1.setDescription("First test class");
        class1.setIdentifier("test-class-1");
        class1.setType("Objekt");

        ClassData class2 = new ClassData();
        class2.setName("TestClass2");
        class2.setDescription("Second test class");
        class2.setIdentifier("test-class-2");
        class2.setType("Subjekt");

        PropertyData prop1 = new PropertyData();
        prop1.setName("testProperty1");
        prop1.setDescription("First test property");
        prop1.setDataType("Řetězec");
        prop1.setDomain("TestClass1");
        prop1.setIdentifier("test-prop-1");

        PropertyData prop2 = new PropertyData();
        prop2.setName("testProperty2");
        prop2.setDescription("Second test property");
        prop2.setDataType("Celé číslo");
        prop2.setDomain("TestClass2");
        prop2.setIdentifier("test-prop-2");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of(class1, class2))
            .properties(List.of(prop1, prop2))
            .relationships(List.of())
            .hierarchies(List.of())
            .build();
    }

    private OntologyData createSyntheticDataWithProperty(PropertyData property) {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Synthetic Property Test");

        ClassData ownerClass = new ClassData();
        ownerClass.setName("OwnerClass");
        ownerClass.setIdentifier("owner-class-1");
        ownerClass.setType("Objekt");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of(ownerClass))
            .properties(List.of(property))
            .relationships(List.of())
            .hierarchies(List.of())
            .build();
    }

    private OntologyData createSyntheticDataWithRelationship(RelationshipData relationship) {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Synthetic Relationship Test");

        ClassData class1 = new ClassData();
        class1.setName("TestClass1");
        class1.setIdentifier("test-class-1");
        class1.setType("Objekt");

        ClassData class2 = new ClassData();
        class2.setName("TestClass2");
        class2.setIdentifier("test-class-2");
        class2.setType("Subjekt");

        return OntologyData.builder()
            .vocabularyMetadata(metadata)
            .classes(List.of(class1, class2))
            .properties(List.of())
            .relationships(List.of(relationship))
            .hierarchies(List.of())
            .build();
    }
}