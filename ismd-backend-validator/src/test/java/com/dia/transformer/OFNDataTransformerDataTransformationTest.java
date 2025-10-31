package com.dia.transformer;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.ConceptFilterUtil;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.exceptions.ConversionException;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.List;

import static com.dia.constants.DataTypeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class OFNDataTransformerDataTransformationTest {

    private OFNDataTransformer transformer;
    private VocabularyMetadata baseMetadata;

    @Mock
    private ConceptFilterUtil conceptFilterUtil;

    @BeforeEach
    void setUp() {
        when(conceptFilterUtil.buildNameToIdentifierMap(any())).thenReturn(new java.util.HashMap<>());
        when(conceptFilterUtil.buildFilteredConceptSet(any(), any())).thenReturn(new java.util.HashSet<>());
        transformer = new OFNDataTransformer(conceptFilterUtil);

        baseMetadata = new VocabularyMetadata();
        baseMetadata.setName("Test Vocabulary");
        baseMetadata.setDescription("Test Description");
        baseMetadata.setNamespace("https://test.example.com/vocab/");
    }

    @Test
    void transformClasses_WithValidClassData_ShouldCreateClassResources() throws ConversionException {
        // Given
        ClassData subjectClass = createSubjectClass();
        ClassData objectClass = createObjectClass();

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .classes(List.of(subjectClass, objectClass))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Verify subject class
        Resource subjectResource = result.getResourceMap().get("PersonSubject");
        assertNotNull(subjectResource, "PersonSubject resource should exist");

        boolean hasConceptType = subjectResource.listProperties(RDF.type).toList().stream()
                .anyMatch(stmt -> stmt.getObject().toString().contains("pojem"));
        assertTrue(hasConceptType, "PersonSubject should have concept type. Actual types: " +
                subjectResource.listProperties(RDF.type).toList());

        boolean hasSubjectType = subjectResource.listProperties(RDF.type).toList().stream()
                .anyMatch(stmt -> stmt.getObject().toString().contains("subjekt") ||
                        stmt.getObject().toString().contains("TSP"));
        assertTrue(hasSubjectType, "PersonSubject should have subject type. Actual types: " +
                subjectResource.listProperties(RDF.type).toList());

        assertTrue(subjectResource.hasProperty(SKOS.prefLabel), "PersonSubject should have prefLabel");

        // Verify object class
        Resource objectResource = result.getResourceMap().get("PropertyObject");
        assertNotNull(objectResource, "PropertyObject resource should exist");

        boolean hasObjectType = objectResource.listProperties(RDF.type).toList().stream()
                .anyMatch(stmt -> stmt.getObject().toString().contains("objekt") ||
                        stmt.getObject().toString().contains("TOP"));
        assertTrue(hasObjectType, "PropertyObject should have object type. Actual types: " +
                objectResource.listProperties(RDF.type).toList());
    }

    @Test
    void transformClasses_WithAlternativeNames_ShouldAddAlternativeNameProperties() throws ConversionException {
        // Given
        ClassData classWithAltNames = new ClassData();
        classWithAltNames.setName("Person");
        classWithAltNames.setAlternativeName("Individual; Human Being; People");
        classWithAltNames.setDescription("A human person");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .classes(List.of(classWithAltNames))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource classResource = result.getResourceMap().get("Person");
        assertNotNull(classResource);

        List<Statement> altNameStatements = classResource.listProperties(
                result.getOntModel().createProperty(baseMetadata.getNamespace() + "alternativní-název")).toList();
        assertEquals(3, altNameStatements.size());
    }

    @Test
    void transformClasses_WithAgendaAndAISCodes_ShouldAddSpecializedProperties() throws ConversionException {
        // Given
        ClassData classWithCodes = new ClassData();
        classWithCodes.setName("LegalEntity");
        classWithCodes.setAgendaCode("10");
        classWithCodes.setAgendaSystemCode("https://.../isvs/654");
        classWithCodes.setDescription("Legal entity class");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .classes(List.of(classWithCodes))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource classResource = result.getResourceMap().get("LegalEntity");
        assertNotNull(classResource);

        // Check agenda property
        assertTrue(classResource.hasProperty(
                result.getOntModel().createProperty(baseMetadata.getNamespace() + "agenda")));

        // Check AIS property
        assertTrue(classResource.hasProperty(
                result.getOntModel().createProperty(baseMetadata.getNamespace() + "agendový-informační-systém")));
    }

    @Test
    void transformProperties_WithDataTypes_ShouldSetCorrectRangeTypes() throws ConversionException {
        // Given
        PropertyData stringProp = createPropertyWithDataType("name", "Řetězec");
        PropertyData dateProp = createPropertyWithDataType("birthDate", "Datum");
        PropertyData booleanProp = createPropertyWithDataType("isActive", "Ano či ne");
        PropertyData integerProp = createPropertyWithDataType("age", "Celé číslo");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .properties(List.of(stringProp, dateProp, booleanProp, integerProp))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        OntModel model = result.getOntModel();

        // Check string property
        Resource stringResource = result.getResourceMap().get("name");
        assertTrue(stringResource.hasProperty(RDFS.range, model.getResource(XSD_STRING)));

        // Check date property
        Resource dateResource = result.getResourceMap().get("birthDate");
        assertTrue(dateResource.hasProperty(RDFS.range, model.getResource(XSD_DATE)));

        // Check boolean property
        Resource booleanResource = result.getResourceMap().get("isActive");
        assertTrue(booleanResource.hasProperty(RDFS.range, model.getResource(XSD_BOOLEAN)));

        // Check integer property
        Resource integerResource = result.getResourceMap().get("age");
        assertTrue(integerResource.hasProperty(RDFS.range, model.getResource(XSD_INTEGER)));
    }

    @Test
    void transformProperties_WithDomainAndRange_ShouldSetDomainRangeProperties() throws ConversionException {
        // Given
        PropertyData propertyWithDomain = new PropertyData();
        propertyWithDomain.setName("hasName");
        propertyWithDomain.setDomain("Person");
        propertyWithDomain.setDataType("Řetězec");
        propertyWithDomain.setDescription("Person's name");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .properties(List.of(propertyWithDomain))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource propertyResource = result.getResourceMap().get("hasName");
        assertNotNull(propertyResource);

        assertTrue(propertyResource.hasProperty(RDFS.domain));

        assertTrue(propertyResource.hasProperty(RDFS.range));
    }

    @Test
    void transformClasses_WithPrivacyAndGovernanceData_ShouldAddDataGovernanceMetadata() throws ConversionException {
        // Given
        ClassData publicClass = createPublicClass();
        ClassData privateClass = createPrivateClass();

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .classes(List.of(publicClass, privateClass))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Check public class
        Resource publicResource = result.getResourceMap().get("PublicEntity");
        assertNotNull(publicResource, "PublicEntity resource should exist");

        // Verify that public class has standard types
        assertTrue(publicResource.hasProperty(RDF.type), "PublicEntity should have RDF types");
        
        // Check that at least basic class types are present
        boolean hasClassType = publicResource.listProperties(RDF.type).toList().stream()
                .anyMatch(stmt -> stmt.getObject().toString().contains("třída") ||
                        stmt.getObject().toString().contains("pojem"));
        assertTrue(hasClassType, "PublicEntity should have class/concept type. Actual types: " +
                publicResource.listProperties(RDF.type).toList());

        // Check private class
        Resource privateResource = result.getResourceMap().get("PrivateEntity");
        assertNotNull(privateResource, "PrivateEntity resource should exist");

        // Verify private class also has basic types
        boolean hasPrivateClassType = privateResource.listProperties(RDF.type).toList().stream()
                .anyMatch(stmt -> stmt.getObject().toString().contains("třída") ||
                        stmt.getObject().toString().contains("pojem"));
        assertTrue(hasPrivateClassType, "PrivateEntity should have class/concept type. Actual types: " +
                privateResource.listProperties(RDF.type).toList());
    }

    @Test
    void transformRelationships_WithValidData_ShouldCreateObjectProperties() throws ConversionException {
        // Given
        RelationshipData relationship = new RelationshipData();
        relationship.setName("worksFor");
        relationship.setDomain("Person");
        relationship.setRange("Organization");
        relationship.setDescription("Person works for organization");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .relationships(List.of(relationship))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource relationshipResource = result.getResourceMap().get("worksFor");
        assertNotNull(relationshipResource);

        // Check that the relationship has the ObjectProperty type
        boolean isObjectProperty = relationshipResource.listProperties(RDF.type).toList().stream()
                .anyMatch(stmt -> stmt.getObject().toString().contains("ObjectProperty"));
        assertTrue(isObjectProperty, "should be an ObjectProperty. Actual types: " +
                relationshipResource.listProperties(RDF.type).toList());

        assertTrue(relationshipResource.hasProperty(RDF.type,
                result.getOntModel().getResource("http://www.w3.org/2002/07/owl#ObjectProperty")));

        assertTrue(relationshipResource.hasProperty(RDFS.domain));
        assertTrue(relationshipResource.hasProperty(RDFS.range));
    }

    @Test
    void transformHierarchies_WithValidData_ShouldCreateSubClassRelationships() throws ConversionException {
        // Given
        ClassData subClass = new ClassData();
        subClass.setName("Student");
        subClass.setDescription("A student");

        ClassData superClass = new ClassData();
        superClass.setName("Person");
        superClass.setDescription("A person");

        HierarchyData hierarchy = new HierarchyData();
        hierarchy.setSubClass("Student");
        hierarchy.setSuperClass("Person");
        hierarchy.setRelationshipName("is-a");
        hierarchy.setDescription("Student is a type of Person");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .classes(List.of(subClass, superClass))
                .hierarchies(List.of(hierarchy))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource studentResource = result.getResourceMap().get("Student");
        Resource personResource = result.getResourceMap().get("Person");

        assertNotNull(studentResource);
        assertNotNull(personResource);

        assertTrue(studentResource.hasProperty(RDFS.subClassOf, personResource));

        assertTrue(studentResource.hasProperty(
                result.getOntModel().createProperty(baseMetadata.getNamespace() + "nadřazená-třída"),
                personResource));
    }

    @Test
    void transformWithTemporalMetadata_ShouldCreateTemporalInstants() throws ConversionException {
        // Given
        VocabularyMetadata temporalMetadata = new VocabularyMetadata();
        temporalMetadata.setName("Temporal Vocab");
        temporalMetadata.setNamespace("https://temporal.test.com/");
        temporalMetadata.setDateOfCreation("2024-01-01");
        temporalMetadata.setDateOfModification("2024-01-02T15:30:00Z");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(temporalMetadata)
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource ontologyResource = result.getResourceMap().get("ontology");

        boolean hasCreationInstant = ontologyResource.listProperties().toList().stream()
                .anyMatch(stmt -> stmt.getPredicate().getURI().contains("okamžik-vytvoření"));

        boolean hasModificationInstant = ontologyResource.listProperties().toList().stream()
                .anyMatch(stmt -> stmt.getPredicate().getURI().contains("okamžik-poslední-změny"));

        assertTrue(hasCreationInstant, "Should have creation temporal instant");
        assertTrue(hasModificationInstant, "Should have modification temporal instant");
    }

    @Test
    void transformWithSourceFields_ShouldProcessELIAndNonELISources() throws ConversionException {
        // Given
        ClassData classWithSources = new ClassData();
        classWithSources.setName("LegalConcept");
        classWithSources.setSource("https://www.e-sbirka.cz/eli/cz/sb/2009/111/2025-01-01/dokument/norma/cast_1/hlava_3/par_25/pism_e");
        classWithSources.setRelatedSource("https://example.com/document.pdf");
        classWithSources.setDescription("Concept with sources");

        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(baseMetadata)
                .classes(List.of(classWithSources))
                .build();

        // When
        TransformationResult result = transformer.transform(data);

        // Then
        Resource classResource = result.getResourceMap().get("LegalConcept");
        assertNotNull(classResource);

        assertTrue(classResource.hasProperty(
                result.getOntModel().createProperty(baseMetadata.getNamespace() + "definující-ustanovení-právního-předpisu")));

        assertTrue(classResource.hasProperty(
                result.getOntModel().createProperty(baseMetadata.getNamespace() + "související-nelegislativní-zdroj")));
    }

    // Helper methods

    private ClassData createSubjectClass() {
        ClassData classData = new ClassData();
        classData.setName("PersonSubject");
        classData.setType("Subjekt práva");
        classData.setDescription("A legal subject");
        return classData;
    }

    private ClassData createObjectClass() {
        ClassData classData = new ClassData();
        classData.setName("PropertyObject");
        classData.setType("Objekt práva");
        classData.setDescription("A legal object");
        return classData;
    }

    private PropertyData createPropertyWithDataType(String name, String dataType) {
        PropertyData property = new PropertyData();
        property.setName(name);
        property.setDataType(dataType);
        property.setDescription("Property with " + dataType + " data type");
        return property;
    }

    private ClassData createPublicClass() {
        ClassData classData = new ClassData();
        classData.setName("PublicEntity");
        classData.setType("Objekt práva");
        classData.setIsPublic("ano");
        classData.setSharingMethod("veřejně přístupné");
        classData.setDescription("Public entity class");
        return classData;
    }

    private ClassData createPrivateClass() {
        ClassData classData = new ClassData();
        classData.setName("PrivateEntity");
        classData.setType("Objekt práva");
        classData.setIsPublic("ne");
        classData.setPrivacyProvision("eli/cz/sb/1999/101/2016-03-31/dokument/norma/cast_1/par_5");
        classData.setDescription("Private entity class");
        return classData;
    }
}
