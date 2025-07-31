package com.dia.conversion.reader.ssp;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ssp.config.SPARQLConfiguration;
import com.dia.conversion.reader.ssp.data.ConceptData;
import com.dia.conversion.reader.ssp.data.DomainRangeInfo;
import com.dia.exceptions.ConversionException;
import com.dia.utility.UtilityMethods;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.apache.jena.sparql.exec.http.QuerySendMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dia.constants.SSPConstants.*;
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

            OntologyData.Builder builder = OntologyData.builder()
                    .vocabularyMetadata(metadata);

            List<ClassData> classes = new ArrayList<>();
            List<PropertyData> properties = new ArrayList<>();
            List<RelationshipData> relationships = new ArrayList<>();

            for (Map.Entry<String, ConceptData> entry : concepts.entrySet()) {
                String conceptIRI = entry.getKey();
                ConceptData concept = entry.getValue();
                String type = conceptTypes.get(conceptIRI);

                if (type != null) {
                    switch (type) {
                        case Z_SGOV_NAMESPACE + "typ-objektu" -> {
                            ClassData classData = convertToClassData(concept, conceptIRI);
                            classes.add(classData);
                        }
                        case Z_SGOV_NAMESPACE + "typ-vlastnosti" -> {
                            PropertyData propertyData = convertToPropertyData(concept, conceptIRI, domainRangeMap);
                            properties.add(propertyData);
                        }
                        case Z_SGOV_NAMESPACE + "typ-vztahu" -> {
                            RelationshipData relationshipData = convertToRelationshipData(concept, conceptIRI, domainRangeMap);
                            relationships.add(relationshipData);
                        }
                        default -> log.debug("Skipping concept with unsupported type: {} - {}", conceptIRI, type);
                    }
                }
            }

            List<HierarchyData> hierarchies = readHierarchies(namespace, concepts);

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

    private VocabularyMetadata readVocabularyMetadata(String ontologyIRI) {
        log.debug("Reading vocabulary metadata for: {}", ontologyIRI);
        VocabularyMetadata metadata = new VocabularyMetadata();

        String queryString = String.format(VOCABULARY_METADATA_QUERY, ontologyIRI);

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {
                ResultSet results = qexec.execSelect();

                if (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();

                    String title = getStringValue(solution, "title");
                    String description = getStringValue(solution, "description");

                    metadata.setName(title != null ? title : UtilityMethods.extractNameFromIRI(ontologyIRI));
                    metadata.setDescription(description);
                    metadata.setNamespace(ontologyIRI);

                    return metadata;
                }
            }
        } catch (Exception e) {
            log.error("Error reading vocabulary metadata", e);
        }
        metadata.setName(UtilityMethods.extractNameFromIRI(ontologyIRI));
        metadata.setNamespace(ontologyIRI);

        return metadata;
    }

    private Map<String, ConceptData> readVocabularyConcepts(String namespace) {
        log.debug("Reading vocabulary concepts for namespace: {}", namespace);

        String queryString = String.format(VOCABULARY_CONCEPTS_QUERY, namespace);
        Map<String, ConceptData> concepts = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {
                ResultSet results = qexec.execSelect();

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();

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
            }
        } catch (Exception e) {
            log.error("Error reading vocabulary concepts", e);
        }

        log.debug("Found {} concepts", concepts.size());
        return concepts;
    }

    private Map<String, String> readConceptTypes(String namespace) {
        log.debug("Reading concept types for namespace: {}", namespace);

        String queryString = String.format(MODEL_TYPES_QUERY, namespace);
        Map<String, String> conceptTypes = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {
                ResultSet results = qexec.execSelect();

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();

                    String conceptIRI = solution.getResource("concept").getURI();
                    String type = solution.getResource("type").getURI();

                    conceptTypes.put(conceptIRI, type);
                }
            }
        } catch (Exception e) {
            log.error("Error reading concept types", e);
        }

        log.debug("Found types for {} concepts", conceptTypes.size());
        return conceptTypes;
    }

    private Map<String, DomainRangeInfo> readDomainRangeInfo(String namespace) {
        log.debug("Reading domain/range information for namespace: {}", namespace);

        String queryString = String.format(DOMAIN_RANGE_QUERY, namespace);
        Map<String, DomainRangeInfo> domainRangeMap = new HashMap<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {
                ResultSet results = qexec.execSelect();

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();

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
            }
        } catch (Exception e) {
            log.error("Error reading domain/range information", e);
        }

        log.debug("Found domain/range info for {} concepts", domainRangeMap.size());
        return domainRangeMap;
    }

    private List<HierarchyData> readHierarchies(String namespace, Map<String, ConceptData> concepts) {
        log.debug("Reading hierarchies for namespace: {}", namespace);

        String queryString = String.format(HIERARCHY_QUERY, namespace, namespace);
        List<HierarchyData> hierarchies = new ArrayList<>();

        try {
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {
                ResultSet results = qexec.execSelect();

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();

                    String subClassIRI = solution.getResource("subClass").getURI();
                    String superClassIRI = solution.getResource("superClass").getURI();

                    if (concepts.containsKey(subClassIRI) && concepts.containsKey(superClassIRI)) {
                        HierarchyData hierarchy = new HierarchyData();
                        hierarchy.setSubClass(UtilityMethods.extractNameFromIRI(subClassIRI));
                        hierarchy.setSuperClass(UtilityMethods.extractNameFromIRI(superClassIRI));
                        hierarchy.setRelationshipName("is-a");
                        hierarchies.add(hierarchy);
                    }
                }
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

    private RelationshipData convertToRelationshipData(ConceptData concept, String conceptIRI, Map<String, DomainRangeInfo> domainRangeMap) {
        RelationshipData relationshipData = new RelationshipData();
        relationshipData.setName(concept.getName());
        relationshipData.setDefinition(concept.getDefinition());
        relationshipData.setIdentifier(conceptIRI);
        relationshipData.setSource(concept.getSource());

        if (!concept.getAlternativeNames().isEmpty()) {
            relationshipData.setAlternativeName(String.join(";", concept.getAlternativeNames()));
        }

        DomainRangeInfo domainRange = domainRangeMap.get(conceptIRI);
        if (domainRange != null) {
            if (domainRange.getDomain() != null) {
                relationshipData.setDomain(UtilityMethods.extractNameFromIRI(domainRange.getDomain()));
            }
            if (domainRange.getRange() != null) {
                relationshipData.setRange(UtilityMethods.extractNameFromIRI(domainRange.getRange()));
            }
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
}