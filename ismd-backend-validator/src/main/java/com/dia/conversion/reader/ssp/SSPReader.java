package com.dia.conversion.reader.ssp;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ssp.config.SPARQLConfiguration;
import com.dia.conversion.reader.ssp.data.ConceptData;
import com.dia.conversion.reader.ssp.data.DomainRangeInfo;
import com.dia.exceptions.ConversionException;
import com.dia.utility.UtilityMethods;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.apache.jena.sparql.exec.http.QuerySendMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dia.conversion.reader.ssp.queries.SPARQLQueries.*;

@Component
@Data
@RequiredArgsConstructor
@Slf4j
public class SSPReader {

    private final SPARQLConfiguration config;

    /**
     * Reads an ontology from a SPARQL endpoint by IRI and converts it to OFN format
     * Uses RDFConnection
     */
    public OntologyData readOntology(String ontologyIRI) throws ConversionException {
        log.info("Reading ontology from IRI: {} using RDFConnection", ontologyIRI);

        try {
            if (!UtilityMethods.isValidIRI(ontologyIRI)) {
                throw new ConversionException("Invalid ontology IRI: " + ontologyIRI);
            }

            String namespace = UtilityMethods.extractNamespace(ontologyIRI);
            log.debug("Extracted namespace: {}", namespace);

            VocabularyMetadata metadata = readVocabularyMetadata(ontologyIRI);
            Map<String, ConceptData> concepts = readVocabularyConcepts(namespace);
            Map<String, String> conceptTypes = readConceptTypes(namespace);
            Map<String, DomainRangeInfo> domainRangeMap = readDomainRangeInfo(namespace);
            Map<String, RelationshipInfo> relationshipInfos = readRelationshipElements(namespace);

            OntologyData.Builder builder = OntologyData.builder().vocabularyMetadata(metadata);
            List<ClassData> classes = new ArrayList<>();
            List<PropertyData> properties = new ArrayList<>();
            List<RelationshipData> relationships = new ArrayList<>();

            int classesCreated = 0;
            int propertiesCreated = 0;
            int relationshipsCreated = 0;

            for (Map.Entry<String, ConceptData> entry : concepts.entrySet()) {
                String conceptIRI = entry.getKey();
                ConceptData concept = entry.getValue();
                String type = conceptTypes.get(conceptIRI);

                if (type != null) {
                    switch (type) {
                        case "https://slovník.gov.cz/základní/pojem/typ-objektu" -> {
                            ClassData classData = convertToClassData(concept, conceptIRI);
                            classes.add(classData);
                            classesCreated++;
                            log.debug("Created class #{}: name='{}', identifier='{}'", classesCreated, classData.getName(), classData.getIdentifier());
                        }
                        case "https://slovník.gov.cz/základní/pojem/typ-vlastnosti" -> {
                            PropertyData propertyData = convertToPropertyData(concept, conceptIRI, domainRangeMap);
                            properties.add(propertyData);
                            propertiesCreated++;
                            log.debug("Created property #{}: name='{}', identifier='{}'", propertiesCreated, propertyData.getName(), propertyData.getIdentifier());
                        }
                        case "https://slovník.gov.cz/základní/pojem/typ-vztahu" -> {
                            RelationshipData relationshipData = convertToRelationshipData(concept, conceptIRI, relationshipInfos);
                            relationships.add(relationshipData);
                            relationshipsCreated++;
                            log.debug("Created relationship #{}: name='{}', domain='{}', range='{}', identifier='{}'",
                                    relationshipsCreated, relationshipData.getName(), relationshipData.getDomain(),
                                    relationshipData.getRange(), relationshipData.getIdentifier());
                        }
                        default -> log.debug("Skipping concept with unsupported type: {} - {}", conceptIRI, type);
                    }
                }
            }

            List<HierarchyData> hierarchies = readHierarchies(namespace, concepts);

            log.info("Ontology conversion completed: {} classes, {} properties, {} relationships, {} hierarchies",
                    classesCreated, propertiesCreated, relationshipsCreated, hierarchies.size());

            return builder
                    .classes(classes)
                    .properties(properties)
                    .relationships(relationships)
                    .hierarchies(hierarchies)
                    .build();

        } catch (Exception e) {
            log.error("Failed to read ontology from IRI: {}", ontologyIRI, e);
            throw new ConversionException("Failed to read ontology from IRI: " + ontologyIRI, e);
        }
    }

    public VocabularyMetadata readVocabularyMetadata(String ontologyIRI) {
        log.debug("Reading vocabulary metadata for: {}", ontologyIRI);

        VocabularyMetadata metadata = new VocabularyMetadata();
        String queryString = String.format(VOCABULARY_METADATA_QUERY, ontologyIRI);
        log.debug("Executing vocabulary metadata query: {}", queryString);

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                log.debug("Executing SPARQL query for vocabulary metadata...");
                ResultSet results = qexec.execSelect();

                if (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    log.debug("Found vocabulary metadata result. Solution variables: {}", solution.varNames());

                    String title = getStringValue(solution, "title");
                    String description = getStringValue(solution, "description");

                    log.debug("Retrieved metadata - Title: '{}', Description: '{}'", title, description);

                    metadata.setName(title != null ? title : UtilityMethods.extractNameFromIRI(ontologyIRI));
                    metadata.setDescription(description);
                    metadata.setNamespace(ontologyIRI);

                    log.debug("Created vocabulary metadata: Name='{}', Namespace='{}', Description='{}'",
                            metadata.getName(), metadata.getNamespace(), metadata.getDescription());
                    return metadata;
                } else {
                    log.debug("No vocabulary metadata found for IRI: {}", ontologyIRI);
                }
            }
        } catch (Exception e) {
            log.error("Error reading vocabulary metadata", e);
        }

        metadata.setName(UtilityMethods.extractNameFromIRI(ontologyIRI));
        metadata.setNamespace(ontologyIRI);
        log.debug("Using fallback metadata: Name='{}', Namespace='{}'", metadata.getName(), metadata.getNamespace());

        return metadata;
    }

    public Map<String, ConceptData> readVocabularyConcepts(String namespace) {
        log.debug("Reading vocabulary concepts for namespace: {}", namespace);

        // Try primary query first
        Map<String, ConceptData> concepts = executeVocabularyConceptsQuery(namespace, VOCABULARY_CONCEPTS_QUERY, true);
        
        // If no results, try fallback query
        if (concepts.isEmpty()) {
            log.debug("Primary vocabulary concepts query returned no results, trying fallback query");
            concepts = executeVocabularyConceptsQuery(namespace, VOCABULARY_CONCEPTS_FALLBACK_QUERY, false);
        }

        log.debug("Found {} unique concepts", concepts.size());
        return concepts;
    }

    private Map<String, ConceptData> executeVocabularyConceptsQuery(String namespace, String queryTemplate, boolean useGraphPattern) {
        String queryString = String.format(queryTemplate, namespace);
        log.debug("Executing vocabulary concepts query: {}", queryString);
        Map<String, ConceptData> concepts = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                log.debug("Executing SPARQL query for vocabulary concepts...");
                ResultSet results = qexec.execSelect();

                int resultCount = 0;
                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    resultCount++;

                    String conceptIRI = solution.getResource("concept").getURI();
                    String prefLabel = getStringValue(solution, "prefLabel");
                    String altLabel = getStringValue(solution, "altLabel");
                    String definition = getStringValue(solution, "definition");
                    String source = getStringValue(solution, "source");
                    String scopeNote = getStringValue(solution, "scopeNote");
                    String broader = solution.contains("broader") ?
                            solution.getResource("broader").getURI() : null;

                    ConceptData concept = concepts.computeIfAbsent(conceptIRI, k -> new ConceptData());
                    concept.setIri(conceptIRI);
                    concept.setName(prefLabel != null ? prefLabel : UtilityMethods.extractNameFromIRI(conceptIRI));
                    concept.setDefinition(definition);
                    concept.setSource(source);
                    concept.setNote(scopeNote);
                    concept.setBroader(broader);

                    if (altLabel != null) {
                        concept.addAlternativeName(altLabel);
                    }
                }

                log.debug("Processed {} vocabulary concept results", resultCount);
            }
        } catch (Exception e) {
            log.error("Error reading vocabulary concepts with query pattern: {}", useGraphPattern ? "GRAPH" : "FALLBACK", e);
        }

        return concepts;
    }

    public Map<String, String> readConceptTypes(String namespace) {
        log.debug("Reading concept types for namespace: {}", namespace);

        Map<String, String> conceptTypes;

        // Try primary query first
        conceptTypes = executeConceptTypesQuery(namespace, MODEL_TYPES_QUERY, true);
        
        // If no results, try fallback query
        if (conceptTypes.isEmpty()) {
            log.debug("Primary concept types query returned no results, trying fallback query");
            conceptTypes = executeConceptTypesQuery(namespace, MODEL_TYPES_FALLBACK_QUERY, false);
        }

        Map<String, ConceptData> allConcepts = readVocabularyConcepts(namespace);

        for (Map.Entry<String, ConceptData> entry : allConcepts.entrySet()) {
            String conceptIRI = entry.getKey();

            if (conceptTypes.containsKey(conceptIRI)) {
                continue;
            }

            ConceptData concept = entry.getValue();
            String name = concept.getName().toLowerCase();
            String defaultType;

            // Use naming patterns to infer type for untyped concepts
            if (isRelationshipName(name)) {
                defaultType = "https://slovník.gov.cz/základní/pojem/typ-vztahu";
                log.debug("Assigned relationship type to untyped concept: {} ({})", concept.getName(), conceptIRI);
            } else if (isPropertyName(name)) {
                defaultType = "https://slovník.gov.cz/základní/pojem/typ-vlastnosti";
                log.debug("Assigned property type to untyped concept: {} ({})", concept.getName(), conceptIRI);
            } else {
                defaultType = "https://slovník.gov.cz/základní/pojem/typ-objektu";
                log.debug("Assigned object type to untyped concept: {} ({})", concept.getName(), conceptIRI);
            }

            conceptTypes.put(conceptIRI, defaultType);
        }

        log.debug("Total concept types found/assigned: {}", conceptTypes.size());
        return conceptTypes;
    }

    private Map<String, String> executeConceptTypesQuery(String namespace, String queryTemplate, boolean useGraphPattern) {
        String queryString = String.format(queryTemplate, namespace);
        log.debug("Executing concept types query: {}", queryString);
        Map<String, String> conceptTypes = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                ResultSet results = qexec.execSelect();
                int resultCount = 0;

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    resultCount++;

                    String conceptIRI = solution.getResource("concept").getURI();
                    String type = solution.getResource("type").getURI();

                    conceptTypes.put(conceptIRI, type);
                    log.debug("Found explicit type: {} -> {}", conceptIRI, type);
                }

                log.debug("Found {} explicitly typed concepts with pattern: {}", resultCount, useGraphPattern ? "GRAPH" : "FALLBACK");
            }
        } catch (Exception e) {
            log.error("Error reading concept types with query pattern: {}", useGraphPattern ? "GRAPH" : "FALLBACK", e);
        }

        return conceptTypes;
    }

    private boolean isRelationshipName(String name) {
        return name.contains("vykonává") || name.contains("má") || name.contains("je") ||
                name.contains("obsahuje") || name.contains("patří") || name.contains("souvisí") ||
                name.matches(".*uje$") || name.matches(".*ává$") || name.matches(".*í$");
    }


    private boolean isPropertyName(String name) {
        return name.startsWith("má-") || name.startsWith("má ") || name.contains("hodnota") || name.contains("vlastnost") ||
                name.matches(".*\\b(číslo|kód|název|datum|hodnota)\\b.*");
    }

    public Map<String, RelationshipInfo> readRelationshipElements(String namespace) {
        log.debug("Reading relationship elements for namespace: {}", namespace);

        String queryString = String.format(RELATIONSHIP_ELEMENTS_SIMPLE_QUERY, namespace);
        log.debug("Executing relationship elements query: {}", queryString);
        Map<String, RelationshipInfo> relationshipInfos = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                log.debug("Executing SPARQL query for relationship elements...");
                ResultSet results = qexec.execSelect();

                int resultCount = 0;
                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    resultCount++;

                    String relationshipIRI = solution.getResource("relationship").getURI();
                    String property = solution.getResource("property").getURI();
                    String targetClass = solution.getResource("targetClass").getURI();

                    RelationshipInfo info = relationshipInfos.computeIfAbsent(relationshipIRI, k -> new RelationshipInfo());
                    info.setRelationshipIRI(relationshipIRI);

                    if (property.contains("má-vztažený-prvek-1")) {
                        info.setElement1(targetClass);
                        log.debug("Set element1 for {}: {}", relationshipIRI, targetClass);
                    } else if (property.contains("má-vztažený-prvek-2")) {
                        info.setElement2(targetClass);
                        log.debug("Set element2 for {}: {}", relationshipIRI, targetClass);
                    }
                }

                log.debug("Processed {} relationship element results", resultCount);
            }
        } catch (Exception e) {
            log.error("Error reading relationship elements", e);
        }

        log.debug("Found relationship elements for {} relationships", relationshipInfos.size());
        return relationshipInfos;
    }

    public Map<String, DomainRangeInfo> readDomainRangeInfo(String namespace) {
        log.debug("Reading domain/range information for namespace: {}", namespace);

        String queryString = String.format(DOMAIN_RANGE_QUERY, namespace);
        log.debug("Executing domain/range query: {}", queryString);
        Map<String, DomainRangeInfo> domainRangeMap = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                log.debug("Executing SPARQL query for domain/range information...");
                ResultSet results = qexec.execSelect();

                int resultCount = 0;
                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    resultCount++;

                    String conceptIRI = solution.getResource("concept").getURI();
                    String domain = solution.contains("domain") ? solution.getResource("domain").getURI() : null;
                    String range = solution.contains("range") ? solution.getResource("range").getURI() : null;

                    DomainRangeInfo info = domainRangeMap.computeIfAbsent(conceptIRI, k -> new DomainRangeInfo());
                    if (domain != null) {
                        info.setDomain(domain);
                    }
                    if (range != null) {
                        info.setRange(range);
                    }
                }

                log.debug("Processed {} domain/range results", resultCount);
            }
        } catch (Exception e) {
            log.error("Error reading domain/range information", e);
        }

        log.debug("Found domain/range info for {} concepts", domainRangeMap.size());
        return domainRangeMap;
    }

    public List<HierarchyData> readHierarchies(String namespace, Map<String, ConceptData> concepts) {
        log.debug("Reading hierarchies for namespace: {}", namespace);

        String queryString = String.format(HIERARCHY_QUERY, namespace);
        log.debug("Executing hierarchies query: {}", queryString);
        List<HierarchyData> hierarchies = new ArrayList<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                log.debug("Executing SPARQL query for hierarchies...");
                ResultSet results = qexec.execSelect();

                int resultCount = 0;
                int addedCount = 0;
                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    resultCount++;

                    String subClassIRI = solution.getResource("subClass").getURI();
                    String superClassIRI = solution.getResource("superClass").getURI();

                    if (concepts.containsKey(subClassIRI) && concepts.containsKey(superClassIRI)) {
                        HierarchyData hierarchy = new HierarchyData();
                        hierarchy.setSubClass(UtilityMethods.extractNameFromIRI(subClassIRI));
                        hierarchy.setSuperClass(UtilityMethods.extractNameFromIRI(superClassIRI));
                        hierarchy.setRelationshipName("is-a");
                        hierarchies.add(hierarchy);
                        addedCount++;
                    }
                }

                log.debug("Processed {} hierarchy results, added {} valid hierarchies", resultCount, addedCount);
            }
        } catch (Exception e) {
            log.error("Error reading hierarchies", e);
        }

        log.debug("Found {} hierarchical relationships", hierarchies.size());
        return hierarchies;
    }

    private ClassData convertToClassData(ConceptData concept, String conceptIRI) {
        ClassData classData = new ClassData();
        classData.setName(concept.getName());
        classData.setDefinition(concept.getDefinition());
        classData.setIdentifier(conceptIRI);
        classData.setSource(concept.getSource());

        if (!concept.getAlternativeNames().isEmpty()) {
            classData.setAlternativeName(String.join(";", concept.getAlternativeNames()));
        }

        String name = concept.getName().toLowerCase();
        if (name.contains("subjekt") || name.contains("osoba")) {
            classData.setType("Subjekt práva");
        } else if (name.contains("objekt")) {
            classData.setType("Objekt práva");
        }

        return classData;
    }

    private PropertyData convertToPropertyData(ConceptData concept, String conceptIRI, Map<String, DomainRangeInfo> domainRangeMap) {
        PropertyData propertyData = new PropertyData();
        propertyData.setName(concept.getName());
        propertyData.setDefinition(concept.getDefinition());
        propertyData.setIdentifier(conceptIRI);
        propertyData.setSource(concept.getSource());

        if (!concept.getAlternativeNames().isEmpty()) {
            propertyData.setAlternativeName(String.join(";", concept.getAlternativeNames()));
        }

        DomainRangeInfo domainRange = domainRangeMap.get(conceptIRI);
        if (domainRange != null) {
            if (domainRange.getDomain() != null) {
                propertyData.setDomain(UtilityMethods.extractNameFromIRI(domainRange.getDomain()));
            }
            if (domainRange.getRange() != null) {
                propertyData.setDataType(domainRange.getRange());
            }
        }

        return propertyData;
    }

    private RelationshipData convertToRelationshipData(ConceptData concept, String conceptIRI,
                                                       Map<String, RelationshipInfo> relationshipInfos) {
        RelationshipData relationshipData = new RelationshipData();
        relationshipData.setName(concept.getName());
        relationshipData.setDefinition(concept.getDefinition());
        relationshipData.setIdentifier(conceptIRI);
        relationshipData.setSource(concept.getSource());

        if (!concept.getAlternativeNames().isEmpty()) {
            relationshipData.setAlternativeName(String.join(";", concept.getAlternativeNames()));
        }

        RelationshipInfo relInfo = relationshipInfos.get(conceptIRI);
        if (relInfo != null) {
            if (relInfo.getElement1() != null) {
                String domainName = UtilityMethods.extractNameFromIRI(relInfo.getElement1());
                relationshipData.setDomain(domainName);
                log.debug("Set relationship domain: {} -> {}", conceptIRI, domainName);
            }

            if (relInfo.getElement2() != null) {
                String rangeName = UtilityMethods.extractNameFromIRI(relInfo.getElement2());
                relationshipData.setRange(rangeName);
                log.debug("Set relationship range: {} -> {}", conceptIRI, rangeName);
            }

            if (relInfo.getElement1() != null && relInfo.getElement2() == null) {
                String name = UtilityMethods.extractNameFromIRI(relInfo.getElement1());
                relationshipData.setRange(name);
                log.debug("Set reflexive relationship: {} -> domain and range: {}", conceptIRI, name);
            }
        } else {
            log.debug("No relationship info found for: {}", conceptIRI);
        }

        return relationshipData;
    }

    private String getStringValue(QuerySolution solution, String varName) {
        if (solution.contains(varName)) {
            RDFNode node = solution.get(varName);
            if (node.isLiteral()) {
                return node.asLiteral().getString();
            } else if (node.isResource()) {
                return node.asResource().getURI();
            }
        }
        return null;
    }

    @Getter
    @Setter
    public static class RelationshipInfo {
        private String relationshipIRI;
        private String element1;
        private String element2;
        private Map<String, String> restrictions = new HashMap<>();
    }
}