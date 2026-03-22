package com.dia.conversion.reader.ssp;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ssp.config.SPARQLConfiguration;
import com.dia.conversion.reader.ssp.data.ConceptData;
import com.dia.conversion.reader.ssp.data.DomainRangeInfo;
import com.dia.conversion.reader.ssp.data.PropertyDomainInfo;
import com.dia.exceptions.ConversionException;
import com.dia.utility.UtilityMethods;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

    public OntologyData readOntology(String ontologyIRI) throws ConversionException {
        log.info("Reading SGoV ontology from IRI: {}", ontologyIRI);

        try {
            if (!UtilityMethods.isValidIRI(ontologyIRI)) {
                throw new ConversionException("Invalid ontology IRI: " + ontologyIRI);
            }

            log.debug("Using full ontology IRI for filtering: {}", ontologyIRI);

            VocabularyMetadata metadata = readVocabularyMetadata(ontologyIRI);

            Map<String, ConceptData> ownedConcepts = readSGovOwnedConcepts(ontologyIRI);
            log.info("Found {} owned concepts", ownedConcepts.size());

            Map<String, String> conceptTypes = readSGovConceptTypes(ontologyIRI, ownedConcepts);
            log.info("Found {} concept types", conceptTypes.size());

            Map<String, DomainRangeInfo> relationshipDomainRangeInfo = readRelationshipDomainRangeInfo(ontologyIRI);
            Map<String, PropertyDomainInfo> propertyDomainInfo = readPropertyDomainInfo(ontologyIRI);
            log.info("Found {} relationship domain/range definitions", relationshipDomainRangeInfo.size());
            log.info("Found {} property domain definitions", propertyDomainInfo.size());

            List<HierarchyData> hierarchies = readSGovHierarchies(ontologyIRI);
            log.info("Found {} hierarchy relationships", hierarchies.size());

            OntologyData.Builder builder = OntologyData.builder().vocabularyMetadata(metadata);
            List<ClassData> classes = new ArrayList<>();
            List<PropertyData> properties = new ArrayList<>();
            List<RelationshipData> relationships = new ArrayList<>();

            int classesCreated = 0;
            int propertiesCreated = 0;
            int relationshipsCreated = 0;

            for (Map.Entry<String, ConceptData> entry : ownedConcepts.entrySet()) {
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
                            PropertyData propertyData = convertToPropertyData(concept, conceptIRI, propertyDomainInfo.get(conceptIRI));
                            properties.add(propertyData);
                            propertiesCreated++;
                            log.debug("Created property #{}: name='{}', identifier='{}'", propertiesCreated, propertyData.getName(), propertyData.getIdentifier());
                        }
                        case "https://slovník.gov.cz/základní/pojem/typ-vztahu" -> {
                            DomainRangeInfo domainRangeInfo = relationshipDomainRangeInfo.get(conceptIRI);
                            log.info("CONVERSION INPUT: {} -> domainRangeInfo={}", conceptIRI, domainRangeInfo);
                            RelationshipData relationshipData = convertToRelationshipData(concept, conceptIRI, domainRangeInfo);
                            relationships.add(relationshipData);
                            relationshipsCreated++;
                            log.info("CONVERSION OUTPUT: {} -> domain='{}', range='{}'", 
                                    relationshipData.getIdentifier(), relationshipData.getDomain(), relationshipData.getRange());
                        }
                        default -> log.debug("Skipping concept with unsupported type: {} - {}", conceptIRI, type);
                    }
                } else {
                    log.debug("No type found for concept: {} - {}", conceptIRI, concept.getName());
                }
            }

            // Diagnostic summary: show which relationships ended up without domain/range
            int missingDomain = 0;
            int missingRange = 0;
            for (RelationshipData rel : relationships) {
                boolean noDomain = rel.getDomain() == null || rel.getDomain().isBlank();
                boolean noRange = rel.getRange() == null || rel.getRange().isBlank();
                if (noDomain || noRange) {
                    log.warn("RELATIONSHIP MISSING D/R: '{}' [{}] -> domain='{}', range='{}'",
                            rel.getName(), rel.getIdentifier(), rel.getDomain(), rel.getRange());
                }
                if (noDomain) missingDomain++;
                if (noRange) missingRange++;
            }
            log.info("SGoV ontology conversion completed: {} classes, {} properties, {} relationships (missing domain: {}, missing range: {})",
                    classesCreated, propertiesCreated, relationshipsCreated, missingDomain, missingRange);

            return builder
                    .classes(classes)
                    .properties(properties)
                    .relationships(relationships)
                    .hierarchies(hierarchies)
                    .build();

        } catch (Exception e) {
            log.error("Failed to read SGoV ontology from IRI: {}", ontologyIRI, e);
            throw new ConversionException("Failed to read SGoV ontology from IRI: " + ontologyIRI, e);
        }
    }

    public VocabularyMetadata readVocabularyMetadata(String ontologyIRI) {
        log.debug("Reading vocabulary metadata for: {}", ontologyIRI);

        VocabularyMetadata metadata = new VocabularyMetadata();
        
        String queryString = String.format("""
            PREFIX dcterms: <http://purl.org/dc/terms/>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            SELECT DISTINCT ?title ?description WHERE {
                <%s> a owl:Ontology .
                OPTIONAL { <%s> dcterms:title ?title }
                OPTIONAL { <%s> dcterms:description ?description }
            }
            """, ontologyIRI, ontologyIRI, ontologyIRI);
        
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
                    
                    log.debug("Found metadata - Name: '{}', Description: '{}'", metadata.getName(), metadata.getDescription());
                } else {
                    log.debug("No metadata found, using fallback");
                    metadata.setName(UtilityMethods.extractNameFromIRI(ontologyIRI));
                    metadata.setNamespace(ontologyIRI);
                }
            }
        } catch (Exception e) {
            log.error("Error reading vocabulary metadata", e);
            metadata.setName(UtilityMethods.extractNameFromIRI(ontologyIRI));
            metadata.setNamespace(ontologyIRI);
        }

        return metadata;
    }

    public Map<String, ConceptData> readSGovOwnedConcepts(String namespace) {
        log.debug("Reading concepts directly for namespace: {}", namespace);
        
        Map<String, ConceptData> concepts = executeSimpleConceptsQuery(namespace);
        
        if (concepts.isEmpty()) {
            log.debug("Simple concepts query returned no results, trying SGoV owned concepts query");
            concepts = executeSGovConceptsQuery(namespace, SGOV_OWNED_CONCEPTS_ONLY_QUERY);
            
            if (concepts.isEmpty()) {
                log.debug("Owned concepts query returned no results, trying comprehensive query");
                concepts = executeSGovConceptsQuery(namespace, SGOV_COMPREHENSIVE_CONCEPTS_QUERY);
            }
        }
        
        log.debug("Found {} concepts", concepts.size());
        return concepts;
    }

    public Map<String, String> readSGovConceptTypes(String namespace, Map<String, ConceptData> ownedConcepts) {
        log.debug("Reading SGoV concept types for namespace: {}", namespace);
        
        Map<String, String> conceptTypes = executeSGovModelElementsQuery(namespace);
        
        int skippedCount = 0;
        for (Map.Entry<String, ConceptData> entry : ownedConcepts.entrySet()) {
            String conceptIRI = entry.getKey();
            if (!conceptTypes.containsKey(conceptIRI)) {
                skippedCount++;
                log.debug("No explicit type found for concept, skipping: {} - {}", conceptIRI, entry.getValue().getName());
            }
        }

        log.debug("Found {} concept types (explicit), skipped {} concepts without explicit type", conceptTypes.size(), skippedCount);
        return conceptTypes;
    }

    public Map<String, DomainRangeInfo> readRelationshipDomainRangeInfo(String namespace) {
        log.debug("Reading relationship domain/range information for namespace: {}", namespace);
        
        String queryString = String.format(SGOV_SIMPLE_DOMAIN_RANGE_QUERY, namespace);
        log.debug("Executing relationship domain/range query: {}", queryString);
        Map<String, DomainRangeInfo> domainRangeMap = new HashMap<>();

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

                    String relationshipIRI = solution.getResource("relationship").getURI();
                    String domain = solution.contains("domain") ? 
                            solution.getResource("domain").getURI() : null;
                    String range = solution.contains("range") ? 
                            solution.getResource("range").getURI() : null;

                    DomainRangeInfo info = new DomainRangeInfo();
                    info.setDomain(domain);
                    info.setRange(range);
                    
                    domainRangeMap.put(relationshipIRI, info);
                    log.info("DOMAIN/RANGE EXTRACTED: {} -> domain={}, range={}", 
                             relationshipIRI, domain, range);
                }

                log.debug("Found {} relationships with domain/range information", resultCount);
            }
        } catch (Exception e) {
            log.error("Error reading relationship domain/range information", e);
        }

        return domainRangeMap;
    }

    public Map<String, PropertyDomainInfo> readPropertyDomainInfo(String namespace) {
        log.debug("Reading property domain information for namespace: {}", namespace);
        
        String queryString = String.format(SGOV_PROPERTY_DOMAIN_QUERY, namespace);
        log.debug("Executing property domain query: {}", queryString);
        Map<String, PropertyDomainInfo> propertyDomainMap = new HashMap<>();

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

                    String propertyIRI = solution.getResource("property").getURI();
                    String domain = solution.contains("domain") ? 
                            solution.getResource("domain").getURI() : null;
                    String domainLabel = getStringValue(solution, "domainLabel");

                    PropertyDomainInfo info = new PropertyDomainInfo();
                    info.setDomain(domain);
                    info.setDomainLabel(domainLabel);
                    
                    propertyDomainMap.put(propertyIRI, info);
                    log.debug("Found property domain for {}: domain={}, domainLabel={}", 
                             propertyIRI, domain, domainLabel);
                }

                log.debug("Found {} properties with domain information", resultCount);
            }
        } catch (Exception e) {
            log.error("Error reading property domain information", e);
        }

        return propertyDomainMap;
    }

    public List<HierarchyData> readSGovHierarchies(String namespace) {
        log.info("=== READING SGOV HIERARCHIES FOR: {} ===", namespace);
        
        String queryString = String.format(SGOV_OWL_SPECIALIZATION_HIERARCHY_QUERY, namespace);
        log.debug("Executing hierarchy query:\n{}", queryString);
        List<HierarchyData> hierarchies = new ArrayList<>();

        try {
            Query query = QueryFactory.create(queryString);
            log.debug("Query parsed successfully, executing against endpoint: {}", config.getSparqlEndpoint());

            try (QueryExecution qexec = QueryExecutionHTTPBuilder
                    .service(config.getSparqlEndpoint())
                    .query(query)
                    .sendMode(QuerySendMode.asPost)
                    .build()) {

                ResultSet results = qexec.execSelect();
                int resultCount = 0;
                log.debug("Query execution started, processing results...");

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    resultCount++;

                    RDFNode childNode = solution.get("child");
                    RDFNode parentNode = solution.get("parent");

                    if (childNode == null || !childNode.isResource() || parentNode == null || !parentNode.isResource()) {
                        log.debug("Skipping hierarchy result #{}: child or parent is not a resource (child={}, parent={})",
                                resultCount, childNode, parentNode);
                        continue;
                    }

                    String childIRI = childNode.asResource().getURI();
                    String parentIRI = parentNode.asResource().getURI();
                    String childLabel = getStringValue(solution, "childLabel");
                    String parentLabel = getStringValue(solution, "parentLabel");

                    log.debug("Hierarchy result #{}: childIRI='{}', parentIRI='{}', childLabel='{}', parentLabel='{}'",
                             resultCount, childIRI, parentIRI, childLabel, parentLabel);

                    String childName = childLabel != null ? childLabel : UtilityMethods.extractNameFromIRI(childIRI);
                    String parentName = parentLabel != null ? parentLabel : UtilityMethods.extractNameFromIRI(parentIRI);

                    log.debug("Extracted names: childName='{}', parentName='{}'", childName, parentName);

                    HierarchyData hierarchyData = new HierarchyData();
                    hierarchyData.setSubClass(childName);
                    hierarchyData.setSuperClass(parentName);
                    hierarchyData.setSuperClassIRI(parentIRI);
                    hierarchyData.setRelationshipId(childIRI);
                    hierarchyData.setRelationshipName("rdfs:subClassOf");

                    if (hierarchyData.hasValidData()) {
                        hierarchies.add(hierarchyData);
                        log.info("HIERARCHY ADDED: {} -> {} (IRI: {})", childName, parentName, childIRI);
                    } else {
                        log.warn("SKIPPING invalid hierarchy data: child='{}', parent='{}' (childIRI: {}, parentIRI: {})", 
                                childName, parentName, childIRI, parentIRI);
                    }
                }

                log.info("Hierarchy query completed: {} total results, {} valid hierarchies added", 
                        resultCount, hierarchies.size());
            }
        } catch (Exception e) {
            log.error("Error reading SGoV hierarchies for namespace: {}", namespace, e);
            log.error("Query that failed: {}", queryString);
        }

        log.info("=== HIERARCHY EXTRACTION COMPLETE: {} relationships found ===", hierarchies.size());
        return hierarchies;
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

    private Map<String, ConceptData> executeSGovConceptsQuery(String namespace, String queryTemplate) {
        String queryString = String.format(queryTemplate, namespace);
        log.debug("Executing SGoV concepts query: {}", queryString);
        Map<String, ConceptData> concepts = new HashMap<>();

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
                    String prefLabel = getStringValue(solution, "prefLabel");
                    String altLabel = getStringValue(solution, "altLabel");
                    String definition = getStringValue(solution, "definition");
                    String source = getStringValue(solution, "source");
                    String scopeNote = getStringValue(solution, "scopeNote");
                    String broader = solution.contains("broader") ?
                            solution.getResource("broader").getURI() : null;

                    boolean isOwned = true;
                    if (solution.contains("isOwned")) {
                        RDFNode ownedNode = solution.get("isOwned");
                        if (ownedNode.isLiteral()) {
                            String value = ownedNode.asLiteral().getString();
                            isOwned = "1".equals(value) || "true".equals(value);
                        }
                    }

                    if (isOwned) {
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

                        log.debug("Added owned concept: {} - {}", conceptIRI, concept.getName());
                    } else {
                        log.debug("Skipped borrowed concept: {} - {}", conceptIRI, prefLabel);
                    }
                }

                log.debug("Processed {} SGoV concept results, found {} owned concepts", resultCount, concepts.size());
            }
        } catch (Exception e) {
            log.error("Error reading SGoV concepts", e);
        }

        return concepts;
    }

    private Map<String, ConceptData> executeSimpleConceptsQuery(String namespace) {
        String queryString = String.format(com.dia.conversion.reader.ssp.queries.SPARQLQueries.SIMPLE_VOCABULARY_CONCEPTS_QUERY, namespace);
        log.debug("Executing simple concepts query: {}", queryString);
        Map<String, ConceptData> concepts = new HashMap<>();

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

                    log.debug("Added concept: {} - {}", conceptIRI, concept.getName());
                }

                log.debug("Processed {} simple concept results, found {} concepts", resultCount, concepts.size());
            }
        } catch (Exception e) {
            log.error("Error reading simple concepts", e);
        }

        return concepts;
    }

    private Map<String, String> executeSGovModelElementsQuery(String namespace) {
        String queryString = String.format(SGOV_MODEL_ELEMENTS_QUERY, namespace);
        log.debug("Executing SGoV model elements query: {}", queryString);
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

                    String elementIRI = solution.getResource("element").getURI();
                    String type = solution.getResource("type").getURI();

                    conceptTypes.put(elementIRI, type);
                    log.debug("Found SGoV model element type: {} -> {}", elementIRI, type);
                }

                log.debug("Found {} SGoV model elements with explicit types", resultCount);
            }
        } catch (Exception e) {
            log.error("Error reading SGoV model elements", e);
        }

        return conceptTypes;
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

    private PropertyData convertToPropertyData(ConceptData concept, String conceptIRI, PropertyDomainInfo domainInfo) {
        PropertyData propertyData = new PropertyData();
        propertyData.setName(concept.getName());
        propertyData.setDefinition(concept.getDefinition());
        propertyData.setIdentifier(conceptIRI);
        propertyData.setSource(concept.getSource());

        if (!concept.getAlternativeNames().isEmpty()) {
            propertyData.setAlternativeName(String.join(";", concept.getAlternativeNames()));
        }

        if (domainInfo != null) {
            if (domainInfo.getDomain() != null) {
                propertyData.setDomain(UtilityMethods.extractNameFromIRI(domainInfo.getDomain()));
                log.debug("Set property domain: {} -> {}", conceptIRI, domainInfo.getDomain());
            }
            if (domainInfo.getDomainLabel() != null) {
                log.debug("Property domain label: {} -> {}", conceptIRI, domainInfo.getDomainLabel());
            }
        }
        
        return propertyData;
    }

    private RelationshipData convertToRelationshipData(ConceptData concept, String conceptIRI, DomainRangeInfo domainRange) {
        RelationshipData relationshipData = new RelationshipData();
        relationshipData.setName(concept.getName());
        relationshipData.setDefinition(concept.getDefinition());
        relationshipData.setIdentifier(conceptIRI);
        relationshipData.setSource(concept.getSource());

        if (!concept.getAlternativeNames().isEmpty()) {
            relationshipData.setAlternativeName(String.join(";", concept.getAlternativeNames()));
        }

        if (domainRange != null) {
            log.info("PROCESSING DOMAIN/RANGE: {} -> domainRange.domain={}, domainRange.range={}",
                    conceptIRI, domainRange.getDomain(), domainRange.getRange());
            if (domainRange.getDomain() != null) {
                String extractedDomain = UtilityMethods.extractNameFromIRI(domainRange.getDomain());
                relationshipData.setDomain(extractedDomain);
                log.info("SET DOMAIN: {} -> '{}' (from {})", conceptIRI, extractedDomain, domainRange.getDomain());
            }
            if (domainRange.getRange() != null) {
                String extractedRange = UtilityMethods.extractNameFromIRI(domainRange.getRange());
                relationshipData.setRange(extractedRange);
                log.info("SET RANGE: {} -> '{}' (from {})", conceptIRI, extractedRange, domainRange.getRange());
            }
        } else {
            log.info("NO DOMAIN/RANGE INFO: {} -> domainRange is null", conceptIRI);
        }

        return relationshipData;
    }
    
}