package com.dia.reader;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ssp.SSPReader;
import com.dia.conversion.reader.ssp.config.SPARQLConfiguration;
import com.dia.conversion.reader.ssp.data.ConceptData;
import com.dia.conversion.reader.ssp.data.DomainRangeInfo;
import com.dia.exceptions.ConversionException;
import com.dia.utility.UtilityMethods;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SSPReaderTest {

    @Mock
    private SPARQLConfiguration config;

    @Mock
    private QueryExecutionHTTPBuilder builder;

    @Mock
    private QueryExecutionHTTP queryExecution;

    @Mock
    private ResultSet resultSet;

    @Mock
    private QuerySolution querySolution;

    @Mock
    private Resource resource;

    @InjectMocks
    private SSPReader sspReader;

    private static final String VALID_IRI = "https://example.com/ontology";
    private static final String VALID_NAMESPACE = "https://example.com/ontology";
    private static final String SPARQL_ENDPOINT = "https://example.com/sparql";

    @Test
    void testReadOntology_Success() throws ConversionException {
        // Arrange
        try (MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {
            utilityMethods.when(() -> UtilityMethods.isValidIRI(VALID_IRI)).thenReturn(true);
            utilityMethods.when(() -> UtilityMethods.extractNamespace(VALID_IRI)).thenReturn(VALID_NAMESPACE);
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI(anyString())).thenReturn("extracted-name");

            SSPReader spyReader = spy(sspReader);

            VocabularyMetadata metadata = new VocabularyMetadata();
            metadata.setName("Test Ontology");
            metadata.setNamespace(VALID_IRI);
            metadata.setDescription("Test Description");
            doReturn(metadata).when(spyReader).readVocabularyMetadata(VALID_IRI);

            Map<String, ConceptData> concepts = new HashMap<>();
            concepts.put("https://example.com/class1", createTestConceptData("TestClass", "Class definition"));
            concepts.put("https://example.com/property1", createTestConceptData("TestProperty", "Property definition"));
            concepts.put("https://example.com/relationship1", createTestConceptData("TestRelationship", "Relationship definition"));
            doReturn(concepts).when(spyReader).readVocabularyConcepts(VALID_NAMESPACE);

            Map<String, String> conceptTypes = new HashMap<>();
            conceptTypes.put("https://example.com/class1", "https://slovník.gov.cz/základní/pojem/typ-objektu");
            conceptTypes.put("https://example.com/property1", "https://slovník.gov.cz/základní/pojem/typ-vlastnosti");
            conceptTypes.put("https://example.com/relationship1", "https://slovník.gov.cz/základní/pojem/typ-vztahu");
            doReturn(conceptTypes).when(spyReader).readConceptTypes(VALID_NAMESPACE);

            Map<String, DomainRangeInfo> domainRangeMap = new HashMap<>();
            doReturn(domainRangeMap).when(spyReader).readDomainRangeInfo(VALID_NAMESPACE);

            Map<String, SSPReader.RelationshipInfo> relationshipInfos = new HashMap<>();
            doReturn(relationshipInfos).when(spyReader).readRelationshipElements(VALID_NAMESPACE);

            List<HierarchyData> hierarchies = new ArrayList<>();
            doReturn(hierarchies).when(spyReader).readHierarchies(eq(VALID_NAMESPACE), any());

            // Act
            OntologyData result = spyReader.readOntology(VALID_IRI);

            // Assert
            assertNotNull(result);
            assertNotNull(result.getVocabularyMetadata());
            assertEquals("Test Ontology", result.getVocabularyMetadata().getName());
            assertEquals(VALID_IRI, result.getVocabularyMetadata().getNamespace());
            assertEquals(1, result.getClasses().size());
            assertEquals(1, result.getProperties().size());
            assertEquals(1, result.getRelationships().size());
            assertEquals(0, result.getHierarchies().size());
        }
    }

    @Test
    void testReadOntology_InvalidIRI() {
        // Arrange
        try (MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {
            utilityMethods.when(() -> UtilityMethods.isValidIRI(anyString())).thenReturn(false);

            // Act & Assert
            assertThrows(ConversionException.class,
                    () -> sspReader.readOntology("invalid-iri"));
        }
    }

    @Test
    void testReadOntology_ExceptionHandling() {
        // Arrange
        try (MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {
            utilityMethods.when(() -> UtilityMethods.isValidIRI(anyString())).thenReturn(true);
            utilityMethods.when(() -> UtilityMethods.extractNamespace(anyString())).thenThrow(new RuntimeException("Test exception"));

            // Act & Assert
            ConversionException exception = assertThrows(ConversionException.class,
                    () -> sspReader.readOntology(VALID_IRI));
            assertTrue(exception.getMessage().contains("Failed to read ontology from IRI"));
        }
    }

    @Test
    void testReadVocabularyMetadata_WithResults() {
        // Arrange
        when(config.getSparqlEndpoint()).thenReturn(SPARQL_ENDPOINT);

        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<QueryExecutionHTTPBuilder> httpBuilder = mockStatic(QueryExecutionHTTPBuilder.class)) {

            setupQueryMocks(queryFactory, httpBuilder);
            when(resultSet.hasNext()).thenReturn(true).thenReturn(false);
            when(resultSet.nextSolution()).thenReturn(querySolution);
            when(querySolution.varNames()).thenReturn(Arrays.asList("title", "description").iterator());
            when(querySolution.contains("title")).thenReturn(true);
            when(querySolution.contains("description")).thenReturn(true);

            RDFNode titleNode = mock(RDFNode.class);
            RDFNode descriptionNode = mock(RDFNode.class);
            Literal titleLiteral = mock(Literal.class);
            Literal descriptionLiteral = mock(Literal.class);

            when(querySolution.get("title")).thenReturn(titleNode);
            when(querySolution.get("description")).thenReturn(descriptionNode);

            when(titleNode.isLiteral()).thenReturn(true);
            when(titleNode.asLiteral()).thenReturn(titleLiteral);
            when(descriptionNode.isLiteral()).thenReturn(true);
            when(descriptionNode.asLiteral()).thenReturn(descriptionLiteral);

            when(titleLiteral.getString()).thenReturn("Test Title");
            when(descriptionLiteral.getString()).thenReturn("Test Description");

            // Act
            VocabularyMetadata result = sspReader.readVocabularyMetadata(VALID_IRI);

            // Assert
            assertNotNull(result);
            assertEquals("Test Title", result.getName());
            assertEquals("Test Description", result.getDescription());
            assertEquals(VALID_IRI, result.getNamespace());
        }
    }

    @Test
    void testReadVocabularyMetadata_NoResults() {
        // Arrange
        when(config.getSparqlEndpoint()).thenReturn(SPARQL_ENDPOINT);

        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<QueryExecutionHTTPBuilder> httpBuilder = mockStatic(QueryExecutionHTTPBuilder.class);
             MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {

            setupQueryMocks(queryFactory, httpBuilder);
            when(resultSet.hasNext()).thenReturn(false);
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI(VALID_IRI)).thenReturn("extracted-name");

            // Act
            VocabularyMetadata result = sspReader.readVocabularyMetadata(VALID_IRI);

            // Assert
            assertNotNull(result);
            assertEquals("extracted-name", result.getName());
            assertEquals(VALID_IRI, result.getNamespace());
            assertNull(result.getDescription());
        }
    }

    @Test
    void testReadVocabularyMetadata_Exception() {
        // Arrange
        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {

            queryFactory.when(() -> QueryFactory.create(anyString())).thenThrow(new RuntimeException("SPARQL error"));
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI(VALID_IRI)).thenReturn("fallback-name");

            // Act
            VocabularyMetadata result = sspReader.readVocabularyMetadata(VALID_IRI);

            // Assert
            assertNotNull(result);
            assertEquals("fallback-name", result.getName());
            assertEquals(VALID_IRI, result.getNamespace());
        }
    }

    @Test
    void testReadConceptTypes_TypeInference() {
        // Arrange
        when(config.getSparqlEndpoint()).thenReturn(SPARQL_ENDPOINT);

        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<QueryExecutionHTTPBuilder> httpBuilder = mockStatic(QueryExecutionHTTPBuilder.class)) {

            setupQueryMocks(queryFactory, httpBuilder);
            when(resultSet.hasNext()).thenReturn(false);

            SSPReader spyReader = spy(sspReader);

            Map<String, ConceptData> concepts = new HashMap<>();
            concepts.put("https://example.com/relationship", createTestConceptData("vykonává něco", "Relationship"));
            concepts.put("https://example.com/property", createTestConceptData("hodnota", "Property"));
            concepts.put("https://example.com/class", createTestConceptData("pojem", "Class"));

            doReturn(concepts).when(spyReader).readVocabularyConcepts(VALID_NAMESPACE);

            // Act
            Map<String, String> result = spyReader.readConceptTypes(VALID_NAMESPACE);

            // Assert
            assertNotNull(result);
            assertEquals(3, result.size());
        }
    }

    @Test
    void testIsRelationshipName() throws Exception {
        // Arrange
        java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("isRelationshipName", String.class);
        method.setAccessible(true);

        // Test cases that should return true
        assertTrue((Boolean) method.invoke(sspReader, "vykonává něco"));
        assertTrue((Boolean) method.invoke(sspReader, "má vlastnost"));
        assertTrue((Boolean) method.invoke(sspReader, "je typ"));
        assertTrue((Boolean) method.invoke(sspReader, "obsahuje"));
        assertTrue((Boolean) method.invoke(sspReader, "patří"));
        assertTrue((Boolean) method.invoke(sspReader, "souvisí"));
        assertTrue((Boolean) method.invoke(sspReader, "eviduje"));

        // Test cases that should return false
        assertFalse((Boolean) method.invoke(sspReader, "simple word"));
    }

    @Test
    void testIsPropertyName() throws Exception {
        // Arrange
        java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("isPropertyName", String.class);
        method.setAccessible(true);

        // Test cases that should return true
        assertTrue((Boolean) method.invoke(sspReader, "má-vlastnost"));
        assertTrue((Boolean) method.invoke(sspReader, "má vlastnost"));
        assertTrue((Boolean) method.invoke(sspReader, "má číslo"));
        assertTrue((Boolean) method.invoke(sspReader, "má kód"));
        assertTrue((Boolean) method.invoke(sspReader, "má název"));
        assertTrue((Boolean) method.invoke(sspReader, "má datum"));

        // Test cases that should return false
        assertFalse((Boolean) method.invoke(sspReader, "simple concept"));
        assertFalse((Boolean) method.invoke(sspReader, "vykonává"));
    }

    @Test
    void testConvertToClassData() throws Exception {
        // Arrange
        ConceptData concept = createTestConceptData("Test Class", "Test definition");
        concept.getAlternativeNames().add("Alternative Name");
        String conceptIRI = "https://example.com/TestClass";

        java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("convertToClassData", ConceptData.class, String.class);
        method.setAccessible(true);

        // Act
        ClassData result = (ClassData) method.invoke(sspReader, concept, conceptIRI);

        // Assert
        assertNotNull(result);
        assertEquals("Test Class", result.getName());
        assertEquals("Test definition", result.getDefinition());
        assertEquals(conceptIRI, result.getIdentifier());
        assertEquals("Alternative Name", result.getAlternativeName());
    }

    @Test
    void testConvertToClassData_WithSubjectType() throws Exception {
        // Arrange
        ConceptData concept = createTestConceptData("Test Subjekt", "Test definition");
        String conceptIRI = "https://example.com/TestSubjekt";

        java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("convertToClassData", ConceptData.class, String.class);
        method.setAccessible(true);

        // Act
        ClassData result = (ClassData) method.invoke(sspReader, concept, conceptIRI);

        // Assert
        assertNotNull(result);
        assertEquals("Subjekt práva", result.getType());
    }

    @Test
    void testConvertToPropertyData() throws Exception {
        // Arrange
        ConceptData concept = createTestConceptData("Test Property", "Property definition");
        String conceptIRI = "https://example.com/TestProperty";
        Map<String, DomainRangeInfo> domainRangeMap = new HashMap<>();
        DomainRangeInfo domainRange = new DomainRangeInfo();
        domainRange.setDomain("https://example.com/Domain");
        domainRange.setRange("https://example.com/Range");
        domainRangeMap.put(conceptIRI, domainRange);

        try (MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI("https://example.com/Domain")).thenReturn("Domain");

            java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("convertToPropertyData",
                    ConceptData.class, String.class, Map.class);
            method.setAccessible(true);

            // Act
            PropertyData result = (PropertyData) method.invoke(sspReader, concept, conceptIRI, domainRangeMap);

            // Assert
            assertNotNull(result);
            assertEquals("Test Property", result.getName());
            assertEquals("Property definition", result.getDefinition());
            assertEquals(conceptIRI, result.getIdentifier());
            assertEquals("Domain", result.getDomain());
            assertEquals("https://example.com/Range", result.getDataType());
        }
    }

    @Test
    void testConvertToRelationshipData() throws Exception {
        // Arrange
        ConceptData concept = createTestConceptData("Test Relationship", "Relationship definition");
        String conceptIRI = "https://example.com/TestRelationship";
        Map<String, SSPReader.RelationshipInfo> relationshipInfos = new HashMap<>();
        SSPReader.RelationshipInfo relInfo = new SSPReader.RelationshipInfo();
        relInfo.setElement1("https://example.com/Element1");
        relInfo.setElement2("https://example.com/Element2");
        relationshipInfos.put(conceptIRI, relInfo);

        try (MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI("https://example.com/Element1")).thenReturn("Element1");
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI("https://example.com/Element2")).thenReturn("Element2");

            java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("convertToRelationshipData",
                    ConceptData.class, String.class, Map.class);
            method.setAccessible(true);

            // Act
            RelationshipData result = (RelationshipData) method.invoke(sspReader, concept, conceptIRI, relationshipInfos);

            // Assert
            assertNotNull(result);
            assertEquals("Test Relationship", result.getName());
            assertEquals("Relationship definition", result.getDefinition());
            assertEquals(conceptIRI, result.getIdentifier());
            assertEquals("Element1", result.getDomain());
            assertEquals("Element2", result.getRange());
        }
    }

    @Test
    void testConvertToRelationshipData_ReflexiveRelationship() throws Exception {
        // Arrange
        ConceptData concept = createTestConceptData("Test Relationship", "Relationship definition");
        String conceptIRI = "https://example.com/TestRelationship";
        Map<String, SSPReader.RelationshipInfo> relationshipInfos = new HashMap<>();
        SSPReader.RelationshipInfo relInfo = new SSPReader.RelationshipInfo();
        relInfo.setElement1("https://example.com/Element1");
        // element2 is null - reflexive relationship
        relationshipInfos.put(conceptIRI, relInfo);

        try (MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI("https://example.com/Element1")).thenReturn("Element1");

            java.lang.reflect.Method method = SSPReader.class.getDeclaredMethod("convertToRelationshipData",
                    ConceptData.class, String.class, Map.class);
            method.setAccessible(true);

            // Act
            RelationshipData result = (RelationshipData) method.invoke(sspReader, concept, conceptIRI, relationshipInfos);

            // Assert
            assertNotNull(result);
            assertEquals("Element1", result.getDomain());
            assertEquals("Element1", result.getRange());
        }
    }

    @Test
    void testReadRelationshipElements() {
        // Arrange
        when(config.getSparqlEndpoint()).thenReturn(SPARQL_ENDPOINT);

        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<QueryExecutionHTTPBuilder> httpBuilder = mockStatic(QueryExecutionHTTPBuilder.class)) {

            setupQueryMocks(queryFactory, httpBuilder);
            when(resultSet.hasNext()).thenReturn(true).thenReturn(false);
            when(resultSet.nextSolution()).thenReturn(querySolution);
            when(querySolution.getResource("relationship")).thenReturn(resource);
            when(querySolution.getResource("property")).thenReturn(resource);
            when(querySolution.getResource("targetClass")).thenReturn(resource);
            when(resource.getURI()).thenReturn(
                    "https://example.com/relationship1",
                    "https://slovník.gov.cz/základní/pojem/má-vztažený-prvek-1",
                    "https://example.com/target1"
            );

            // Act
            Map<String, SSPReader.RelationshipInfo> result = sspReader.readRelationshipElements(VALID_NAMESPACE);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            SSPReader.RelationshipInfo info = result.get("https://example.com/relationship1");
            assertNotNull(info);
            assertEquals("https://example.com/target1", info.getElement1());
        }
    }

    @Test
    void testReadDomainRangeInfo() {
        // Arrange
        when(config.getSparqlEndpoint()).thenReturn(SPARQL_ENDPOINT);

        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<QueryExecutionHTTPBuilder> httpBuilder = mockStatic(QueryExecutionHTTPBuilder.class)) {

            setupQueryMocks(queryFactory, httpBuilder);
            when(resultSet.hasNext()).thenReturn(true).thenReturn(false);
            when(resultSet.nextSolution()).thenReturn(querySolution);
            when(querySolution.getResource("concept")).thenReturn(resource);
            when(querySolution.contains("domain")).thenReturn(true);
            when(querySolution.contains("range")).thenReturn(true);
            when(querySolution.getResource("domain")).thenReturn(resource);
            when(querySolution.getResource("range")).thenReturn(resource);
            when(resource.getURI()).thenReturn(
                    "https://example.com/concept1",
                    "https://example.com/domain1",
                    "https://example.com/range1"
            );

            // Act
            Map<String, DomainRangeInfo> result = sspReader.readDomainRangeInfo(VALID_NAMESPACE);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            DomainRangeInfo info = result.get("https://example.com/concept1");
            assertNotNull(info);
            assertEquals("https://example.com/domain1", info.getDomain());
            assertEquals("https://example.com/range1", info.getRange());
        }
    }

    @Test
    void testReadHierarchies() {
        // Arrange
        when(config.getSparqlEndpoint()).thenReturn(SPARQL_ENDPOINT);

        Map<String, ConceptData> concepts = new HashMap<>();
        concepts.put("https://example.com/subclass", createTestConceptData("SubClass", "Sub definition"));
        concepts.put("https://example.com/superclass", createTestConceptData("SuperClass", "Super definition"));

        try (MockedStatic<QueryFactory> queryFactory = mockStatic(QueryFactory.class);
             MockedStatic<QueryExecutionHTTPBuilder> httpBuilder = mockStatic(QueryExecutionHTTPBuilder.class);
             MockedStatic<UtilityMethods> utilityMethods = mockStatic(UtilityMethods.class)) {

            setupQueryMocks(queryFactory, httpBuilder);
            when(resultSet.hasNext()).thenReturn(true).thenReturn(false);
            when(resultSet.nextSolution()).thenReturn(querySolution);
            when(querySolution.getResource("subClass")).thenReturn(resource);
            when(querySolution.getResource("superClass")).thenReturn(resource);
            when(resource.getURI()).thenReturn("https://example.com/subclass", "https://example.com/superclass");
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI("https://example.com/subclass")).thenReturn("SubClass");
            utilityMethods.when(() -> UtilityMethods.extractNameFromIRI("https://example.com/superclass")).thenReturn("SuperClass");

            // Act
            List<HierarchyData> result = sspReader.readHierarchies(VALID_NAMESPACE, concepts);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            HierarchyData hierarchy = result.get(0);
            assertEquals("SubClass", hierarchy.getSubClass());
            assertEquals("SuperClass", hierarchy.getSuperClass());
            assertEquals("is-a", hierarchy.getRelationshipName());
        }
    }

    // Helper methods

    private void setupQueryMocks(MockedStatic<QueryFactory> queryFactory,
                                 MockedStatic<QueryExecutionHTTPBuilder> httpBuilder) {
        Query mockQuery = mock(Query.class);
        queryFactory.when(() -> QueryFactory.create(anyString())).thenReturn(mockQuery);
        httpBuilder.when(() -> QueryExecutionHTTPBuilder.service(SPARQL_ENDPOINT)).thenReturn(builder);
        when(builder.query(mockQuery)).thenReturn(builder);
        when(builder.sendMode(any())).thenReturn(builder);
        when(builder.build()).thenReturn(queryExecution);
        when(queryExecution.execSelect()).thenReturn(resultSet);
    }

    private ConceptData createTestConceptData(String name, String definition) {
        ConceptData concept = new ConceptData();
        concept.setName(name);
        concept.setDefinition(definition);
        concept.setSource("test source");
        return concept;
    }
}