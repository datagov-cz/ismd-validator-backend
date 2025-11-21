package com.dia.exporter;

import com.dia.exceptions.TurtleExportException;
import com.dia.utility.UtilityMethods;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.*;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dia.constants.VocabularyConstants.*;
import static com.dia.constants.ExportConstants.Turtle.*;
import static com.dia.constants.ExportConstants.Common.*;
import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;

@Slf4j
public class TurtleExporter {

    private final OntModel ontModel;
    @Getter
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;
    private final String effectiveNamespace;

    private static final String POJEM_IRI = "/pojem/";
    private static final Map<String, String> STANDARD_PREFIXES = new HashMap<>();

    static {
        STANDARD_PREFIXES.put(PREFIX_DCT, DCTerms.getURI());
        STANDARD_PREFIXES.put(PREFIX_OWL, OWL2.getURI());
        STANDARD_PREFIXES.put(PREFIX_RDF, RDF.getURI());
        STANDARD_PREFIXES.put(PREFIX_RDFS, RDFS.getURI());
        STANDARD_PREFIXES.put(PREFIX_SKOS, SKOS.getURI());
        STANDARD_PREFIXES.put(PREFIX_XSD, XSD);
        STANDARD_PREFIXES.put("vsgov", "https://slovník.gov.cz/veřejný-sektor/pojem/");
        STANDARD_PREFIXES.put("l111-2009", "https://slovník.gov.cz/legislativním/sbírka/111/2009/pojem/");
        STANDARD_PREFIXES.put("a104", "https://slovník.gov.cz/agendový/104/pojem/");
        STANDARD_PREFIXES.put("slovníky", "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/");
        STANDARD_PREFIXES.put("čas", CAS_NS);
        STANDARD_PREFIXES.put("schema", "http://schema.org/");
        STANDARD_PREFIXES.put("typ-obsahu-údajů", "https://slovník.gov.cz/legislativní/sbírka/360/2023/pojem/má-typ-obsahu-údaje");
        STANDARD_PREFIXES.put("způsoby-sdílení-údajů", "https://slovník.gov.cz/legislativní/sbírka/360/2023/pojem/má-způsob-sdílení-údaje");
        STANDARD_PREFIXES.put("způsoby-získání-údajů", "https://slovník.gov.cz/legislativní/sbírka/360/2023/pojem/má-způsob-získání-údaje");
    }

    public TurtleExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties, String effectiveNamespace) {
        this.ontModel = ontModel;
        this.resourceMap = new HashMap<>(resourceMap);
        this.modelName = modelName;
        this.modelProperties = modelProperties;
        this.effectiveNamespace = effectiveNamespace;
    }

    public String exportToTurtle() throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export operation: requestId={}, modelName={}", requestId, modelName);
        return handleTurtleOperation(() -> {
            log.debug("Creating transformed model: requestId={}", requestId);
            OntModel transformedModel = createTransformedModel();

            log.debug("Applying transformations: requestId={}", requestId);
            applyTransformations(transformedModel);

            log.debug("Serializing model to Turtle: requestId={}", requestId);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            RDFDataMgr.write(outputStream, transformedModel, RDFFormat.TURTLE_PRETTY);

            log.debug("Turtle serialization completed: requestId={}", requestId);
            return outputStream.toString(StandardCharsets.UTF_8);
        });
    }

    private boolean isEmptyLiteralStatement(Statement stmt) {
        if (stmt.getObject().isLiteral()) {
            Literal lit = stmt.getObject().asLiteral();
            String value = lit.getString();
            return value == null || value.trim().isEmpty();
        }
        return false;
    }

    private void removeEmptyLiterals(OntModel model) {
        List<Statement> toRemove = new ArrayList<>();

        StmtIterator stmtIter = model.listStatements();
        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.next();
            if (isEmptyLiteralStatement(stmt)) {
                toRemove.add(stmt);
                log.debug("Post-processing: removing empty literal statement: {}", stmt);
            }
        }

        model.remove(toRemove);
    }

    private void cleanupSKOSProperties(OntModel model) {
        removeEmptyPropertyValues(model, SKOS.definition);
        removeEmptyPropertyValues(model, SKOS.prefLabel);
    }

    private void removeEmptyPropertyValues(OntModel model, Property property) {
        List<Statement> toRemove = new ArrayList<>();

        StmtIterator stmts = model.listStatements(null, property, (RDFNode) null);
        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (isEmptyLiteralStatement(stmt)) {
                toRemove.add(stmt);
                log.debug("Removing empty {} statement: {}", property.getLocalName(), stmt);
            }
        }

        model.remove(toRemove);
    }

    private void cleanupNamespaceProperties(OntModel model) {
        for (String propName : PropertySets.ALL_PROPERTIES) {
            Property property = model.createProperty(effectiveNamespace + propName);
            removeEmptyPropertyValues(model, property);
        }

        cleanupNewSourceProperties(model);
    }

    private void cleanupNewSourceProperties(OntModel model) {
        String[] newSourceProperties = {
                DEFINUJICI_USTANOVENI,
                SOUVISEJICI_USTANOVENI,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ
        };

        for (String propName : newSourceProperties) {
            Property customProperty = model.createProperty(effectiveNamespace + propName);
            Property defaultProperty = model.createProperty(DEFAULT_NS + propName);

            removeEmptyPropertyValues(model, customProperty);
            removeEmptyPropertyValues(model, defaultProperty);

            log.debug("Cleaned up source property: {}", propName);
        }
    }

    private OntModel createTransformedModel() {
        if (ontModel == null || ontModel.isEmpty()) {
            throw new TurtleExportException("Ontology model is null or empty.");
        }

        OntModel newModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        StmtIterator stmtIter = ontModel.listStatements();
        int originalStatements = 0;
        int filteredStatements = 0;

        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.next();
            originalStatements++;
            boolean shouldSkip = false;

            if (isEmptyLiteralStatement(stmt)) {
                filteredStatements++;
                log.debug("Filtering out empty literal statement: {}", stmt);
                shouldSkip = true;
            } else {
                Resource subject = stmt.getSubject();
                if (isBaseSchemaResource(subject.getURI())) {
                    filteredStatements++;
                    log.debug("Filtering out base schema definition: {}", stmt);
                    shouldSkip = true;
                } else if (isExternalReferenceOnlyResource(subject)) {
                    filteredStatements++;
                    log.debug("Filtering out external reference-only resource: {}", subject.getURI());
                    shouldSkip = true;
                }
            }

            if (shouldSkip) {
                continue;
            }

            newModel.add(stmt);
        }

        log.debug("Model transformation: {} original statements, {} filtered out, {} retained",
                originalStatements, filteredStatements, originalStatements - filteredStatements);

        return newModel;
    }

    private boolean isExternalReferenceOnlyResource(Resource resource) {
        if (resource == null || resource.getURI() == null) {
            return false;
        }

        String uri = resource.getURI();

        return !uri.startsWith(effectiveNamespace);
    }

    private boolean shouldFilterAsBaseSchema(String uri) {
        if (UtilityMethods.isOFNBaseSchemaElement(uri)) {
            return true;
        }

        if (!effectiveNamespace.equals(DEFAULT_NS) && uri.startsWith(DEFAULT_NS) && !uri.startsWith(effectiveNamespace)) {
            if (uri.contains("časový-okamžik-") && isTemporalInstantInUse(uri)) {
                return false;
            }
            return !uri.contains("digitální-dokument-") || isDigitalDocumentInUse(uri);
        }

        return false;
    }

    private boolean isBaseSchemaResource(String uri) {
        if (uri == null) {
            return false;
        }

        if (uri.startsWith("http://www.w3.org/2001/XMLSchema#")) {
            return true;
        }

        if (uri.startsWith("http://schema.org/")) {
            return true;
        }

        if (uri.startsWith("https://slovník.gov.cz/")) {

            if (uri.startsWith("https://slovník.gov.cz/datový") ||
                    uri.startsWith("https://slovník.gov.cz/legislativní") ||
                    uri.startsWith("https://slovník.gov.cz/veřejný-sektor")) {
                return false;
            }

            if (uri.contains("/datový-slovník-ofn-slovníků/pojem/")) {
                return true;
            }

            if (uri.startsWith("https://slovník.gov.cz/agendový") && !uri.contains(POJEM_IRI)) {
                log.debug("Filtering out base schema property: {}", uri);
                return true;
            }

            if (uri.startsWith("https://slovník.gov.cz/agendový") && uri.contains(POJEM_IRI)) {
                return false;
            }

            if (uri.equals("https://slovník.gov.cz/generický/digitální-objekty/pojem/digitální-objekt")) {
                return false;
            } else if ((uri.startsWith("https://slovník.gov.cz/generický") ||
                    uri.startsWith("https://slovník.gov.cz/")) &&
                    !uri.contains(POJEM_IRI) &&
                    !uri.contains("/slovník")) {
                log.debug("Filtering out base schema property: {}", uri);
                return true;
            }
        }

        if (shouldFilterAsBaseSchema(uri)) {
            return true;
        }

        if (uri.contains("časový-okamžik-")) {
            return !isTemporalInstantInUse(uri);
        }

        return uri.contains("digitální-dokument-") && isDigitalDocumentInUse(uri);
    }

    private boolean isTemporalInstantInUse(String uri) {
        Resource instant = ontModel.getResource(uri);
        if (instant == null) return false;

        Property creationProp = ontModel.getProperty(SLOVNIKY_NS + OKAMZIK_VYTVORENI);
        Property modificationProp = ontModel.getProperty(SLOVNIKY_NS + OKAMZIK_POSLEDNI_ZMENY);

        return ontModel.contains(null, creationProp, instant) ||
                ontModel.contains(null, modificationProp, instant);
    }

    private boolean isDigitalDocumentInUse(String uri) {
        Resource document = ontModel.getResource(uri);
        if (document == null) return true;

        Property definingNonLegProp = ontModel.getProperty(DEFAULT_NS + DEFINUJICI_NELEGISLATIVNI_ZDROJ);
        Property relatedNonLegProp = ontModel.getProperty(DEFAULT_NS + SOUVISEJICI_NELEGISLATIVNI_ZDROJ);

        return !ontModel.contains(null, definingNonLegProp, document) &&
                !ontModel.contains(null, relatedNonLegProp, document);
    }

    private void applyTransformations(OntModel transformedModel) {
        setupPrefixes(transformedModel);

        createConceptScheme(transformedModel);

        addTemporalMetadataToConceptScheme(transformedModel);

        transformResourcesToSKOSConcepts(transformedModel);

        transformLabelsToSKOS(transformedModel);

        transformAlternativeNamesToSKOS(transformedModel);

        ensureDomainRangeProperties(transformedModel);

        mapCustomPropertiesToStandard(transformedModel);

        transformSourceProperties(transformedModel);

        addInSchemeRelationships(transformedModel);

        cleanupSKOSProperties(transformedModel);

        cleanupNamespaceProperties(transformedModel);

        removeEmptyLiterals(transformedModel);
    }

    private void setupPrefixes(OntModel transformedModel) {
        for (Map.Entry<String, String> entry : STANDARD_PREFIXES.entrySet()) {
            transformedModel.setNsPrefix(entry.getKey(), entry.getValue());
        }
    }

    private String getOntologyIRI() {
        Resource ontologyResource = resourceMap.get("ontology");
        if (ontologyResource != null) {
            String ontologyIRI = ontologyResource.getURI();
            log.debug("Found ontology IRI from resource map: {}", ontologyIRI);
            return ontologyIRI;
        }

        StmtIterator iter = ontModel.listStatements(null, RDF.type, OWL2.Ontology);
        if (iter.hasNext()) {
            Resource resource = iter.next().getSubject();
            String ontologyIRI = resource.getURI();
            log.debug("Found ontology IRI from model: {}", ontologyIRI);
            return ontologyIRI;
        }

        String catalogNamespace = modelProperties.get(LOKALNI_KATALOG);
        if (catalogNamespace != null && !catalogNamespace.isEmpty()) {
            log.debug("Using catalog namespace as ontology IRI: {}", catalogNamespace);
            return catalogNamespace;
        }

        log.warn("Could not determine ontology IRI from any source");
        return null;
    }

    private void createConceptScheme(OntModel transformedModel) {
        String ontologyIRI = getOntologyIRI();
        if (ontologyIRI == null) {
            log.error("Cannot create ConceptScheme without ontology IRI");
            return;
        }

        Resource ontologyResource;

        StmtIterator iter = transformedModel.listStatements(null, RDF.type, OWL2.Ontology);
        if (iter.hasNext()) {
            ontologyResource = iter.next().getSubject();
            log.debug("Using existing ontology resource: {}", ontologyResource.getURI());
        } else {
            ontologyResource = transformedModel.createResource(ontologyIRI);
            ontologyResource.addProperty(RDF.type, OWL2.Ontology);
            log.debug("Created ontology resource: {}", ontologyIRI);
        }

        if (!ontologyResource.hasProperty(RDF.type, SKOS.ConceptScheme)) {
            ontologyResource.addProperty(RDF.type, SKOS.ConceptScheme);
        }

        Resource slovnikType = transformedModel.createResource("https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/slovník");
        if (!ontologyResource.hasProperty(RDF.type, slovnikType)) {
            ontologyResource.addProperty(RDF.type, slovnikType);
        }

        if (modelName != null && !modelName.isEmpty()) {
            ontologyResource.removeAll(SKOS.prefLabel);
            ontologyResource.addProperty(SKOS.prefLabel, modelName, DEFAULT_LANG);
        }

        String description = modelProperties.getOrDefault(POPIS, "");
        if (description != null && !description.isEmpty()) {
            ontologyResource.removeAll(DCTerms.description);
            ontologyResource.addProperty(DCTerms.description, description, DEFAULT_LANG);
        }

        log.debug("ConceptScheme configured: {}", ontologyResource.getURI());
    }

    private void addTemporalMetadataToConceptScheme(OntModel transformedModel) {
        String ontologyIRI = getOntologyIRI();
        if (ontologyIRI == null) {
            return;
        }

        Resource ontologyResource = transformedModel.getResource(ontologyIRI);
        if (ontologyResource == null) {
            return;
        }

        Resource originalOntologyResource = resourceMap.get("ontology");
        if (originalOntologyResource != null) {
            Property creationProperty = ontModel.getProperty(SLOVNIKY_NS + OKAMZIK_VYTVORENI);
            if (originalOntologyResource.hasProperty(creationProperty)) {
                addCreationDate(originalOntologyResource, ontologyResource, transformedModel, creationProperty);
            }

            Property modificationProperty = ontModel.getProperty(SLOVNIKY_NS + OKAMZIK_POSLEDNI_ZMENY);
            if (originalOntologyResource.hasProperty(modificationProperty)) {
                addModificationDate(originalOntologyResource, ontologyResource, transformedModel, modificationProperty);
            }
        }
    }

    private void addCreationDate(Resource originalOntologyResource, Resource ontologyResource, OntModel transformedModel, Property creationProperty) {
        Statement creationStmt = originalOntologyResource.getProperty(creationProperty);
        if (creationStmt.getObject().isResource()) {
            Resource temporalInstant = creationStmt.getObject().asResource();
            copyTemporalInstant(temporalInstant, transformedModel);

            Property newCreationProperty = transformedModel.createProperty(SLOVNIKY_NS + OKAMZIK_VYTVORENI);
            Resource newTemporalInstant = transformedModel.getResource(temporalInstant.getURI());
            if (newTemporalInstant != null) {
                ontologyResource.addProperty(newCreationProperty, newTemporalInstant);
                log.debug("Added creation temporal metadata to concept scheme");
            }
        }
    }

    private void addModificationDate(Resource originalOntologyResource, Resource ontologyResource, OntModel transformedModel, Property modificationProperty) {
        Statement modificationStmt = originalOntologyResource.getProperty(modificationProperty);
        if (modificationStmt.getObject().isResource()) {
            Resource temporalInstant = modificationStmt.getObject().asResource();
            copyTemporalInstant(temporalInstant, transformedModel);

            Property newModificationProperty = transformedModel.createProperty(SLOVNIKY_NS + OKAMZIK_POSLEDNI_ZMENY);
            Resource newTemporalInstant = transformedModel.getResource(temporalInstant.getURI());
            if (newTemporalInstant != null) {
                ontologyResource.addProperty(newModificationProperty, newTemporalInstant);
                log.debug("Added modification temporal metadata to concept scheme");
            }
        }
    }

    private void copyTemporalInstant(Resource temporalInstant, OntModel transformedModel) {
        Resource newInstant = transformedModel.createResource(temporalInstant.getURI());

        newInstant.addProperty(RDF.type, transformedModel.createResource(CAS_NS + CASOVY_OKAMZIK));

        Property dateProperty = ontModel.getProperty(CAS_NS + DATUM);
        if (temporalInstant.hasProperty(dateProperty)) {
            Statement dateStmt = temporalInstant.getProperty(dateProperty);
            Property newDateProperty = transformedModel.createProperty(CAS_NS + DATUM);
            newInstant.addProperty(newDateProperty, dateStmt.getObject());
        }

        Property dateTimeProperty = ontModel.getProperty(CAS_NS + DATUM_A_CAS);
        if (temporalInstant.hasProperty(dateTimeProperty)) {
            Statement dateTimeStmt = temporalInstant.getProperty(dateTimeProperty);
            Property newDateTimeProperty = transformedModel.createProperty(CAS_NS + DATUM_A_CAS);
            newInstant.addProperty(newDateTimeProperty, dateTimeStmt.getObject());
        }
    }

    private void transformResourcesToSKOSConcepts(OntModel transformedModel) {
        Set<String> baseSchemaClasses = Set.of(
                OFN_NAMESPACE + POJEM,
                OFN_NAMESPACE + VLASTNOST,
                OFN_NAMESPACE + VZTAH,
                OFN_NAMESPACE + TRIDA,
                OFN_NAMESPACE + TSP,
                OFN_NAMESPACE + TOP,
                OFN_NAMESPACE + VEREJNY_UDAJ,
                OFN_NAMESPACE + NEVEREJNY_UDAJ
        );

        ResIterator classResources = transformedModel.listSubjectsWithProperty(RDF.type,
                transformedModel.createResource(OFN_NAMESPACE + TRIDA));

        int conceptCount = 0;
        int filteredCount = 0;

        while (classResources.hasNext()) {
            Resource resource = classResources.next();
            boolean shouldSkip = false;

            if (baseSchemaClasses.contains(resource.getURI())) {
                filteredCount++;
                log.debug("Filtering out base schema class from SKOS concepts: {}", resource.getURI());
                shouldSkip = true;
            } else if (resource.hasProperty(RDF.type, OWL2.ObjectProperty) ||
                    resource.hasProperty(RDF.type, OWL2.DatatypeProperty)) {
                filteredCount++;
                log.debug("Skipping OWL property from SKOS concepts: {}", resource.getURI());
                shouldSkip = true;
            }

            if (shouldSkip) {
                continue;
            }

            if (!resource.hasProperty(RDF.type, SKOS.Concept)) {
                resource.addProperty(RDF.type, SKOS.Concept);
                conceptCount++;
            }

            log.debug("Processing class as SKOS concept: {}", resource.getURI());
        }

        log.debug("Processed {} classes as SKOS concepts, filtered out {} properties/base classes",
                conceptCount, filteredCount);
    }

    private void transformLabelsToSKOS(OntModel transformedModel) {
        Map<Resource, Map<String, String>> resourceLabels = collectResourceLabels(transformedModel);
        transformedModel.removeAll(null, RDFS.label, null);
        applyTransformedLabels(transformedModel, resourceLabels);
        transformDefinitionsToSKOS(transformedModel);
    }

    private Map<Resource, Map<String, String>> collectResourceLabels(OntModel transformedModel) {
        Map<Resource, Map<String, String>> resourceLabels = new HashMap<>();

        StmtIterator labelStmts = transformedModel.listStatements(null, RDFS.label, (RDFNode) null);
        while (labelStmts.hasNext()) {
            Statement stmt = labelStmts.next();
            boolean shouldSkip = false;

            if (!stmt.getObject().isLiteral()) {
                shouldSkip = true;
            } else {
                Literal lit = stmt.getObject().asLiteral();
                String text = lit.getString();

                if (text == null || text.isEmpty()) {
                    shouldSkip = true;
                }
            }

            if (shouldSkip) {
                continue;
            }

            Literal lit = stmt.getObject().asLiteral();
            String text = lit.getString();
            Resource subject = stmt.getSubject();
            String lang = getLanguageOrDefault(lit);
            resourceLabels.computeIfAbsent(subject, k -> new HashMap<>()).put(lang, text);
        }

        return resourceLabels;
    }

    private String getLanguageOrDefault(Literal literal) {
        String lang = literal.getLanguage();
        return (lang == null || lang.isEmpty()) ? DEFAULT_LANG : lang;
    }

    private void applyTransformedLabels(OntModel transformedModel, Map<Resource, Map<String, String>> resourceLabels) {
        for (Map.Entry<Resource, Map<String, String>> entry : resourceLabels.entrySet()) {
            Resource subject = entry.getKey();
            Map<String, String> labels = entry.getValue();

            Property labelProperty = determineLabelProperty(subject);
            addLabelsToResource(transformedModel, subject, labels, labelProperty);
        }
    }

    private Property determineLabelProperty(Resource subject) {
        if (subject.hasProperty(RDF.type, OWL2.ObjectProperty) ||
                subject.hasProperty(RDF.type, OWL2.DatatypeProperty)) {
            return RDFS.label;
        }
        return SKOS.prefLabel;
    }

    private void addLabelsToResource(OntModel transformedModel, Resource subject, Map<String, String> labels, Property labelProperty) {
        for (Map.Entry<String, String> langLabel : labels.entrySet()) {
            subject.addProperty(labelProperty,
                    transformedModel.createLiteral(langLabel.getValue(), langLabel.getKey()));
        }
    }

    private void transformDefinitionsToSKOS(OntModel transformedModel) {
        Property defProperty = transformedModel.createProperty(effectiveNamespace + DEFINICE);
        StmtIterator defStmts = transformedModel.listStatements(null, defProperty, (RDFNode) null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (defStmts.hasNext()) {
            Statement stmt = defStmts.next();
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                String value = lit.getString();

                if (value == null || value.isEmpty()) {
                    toRemove.add(stmt);
                    continue;
                }

                String lang = lit.getLanguage();

                if (lang != null && !lang.isEmpty()) {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            SKOS.definition,
                            transformedModel.createLiteral(value, lang)
                    ));
                } else {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            SKOS.definition,
                            transformedModel.createLiteral(value, DEFAULT_LANG)
                    ));
                }
            }
            toRemove.add(stmt);
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void transformAlternativeNamesToSKOS(OntModel transformedModel) {
        Property altNameProperty = transformedModel.createProperty(effectiveNamespace + ALTERNATIVNI_NAZEV);
        StmtIterator altNameStmts = transformedModel.listStatements(null, altNameProperty, (RDFNode) null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (altNameStmts.hasNext()) {
            Statement stmt = altNameStmts.next();
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                String value = lit.getString();

                if (value == null || value.isEmpty()) {
                    toRemove.add(stmt);
                    continue;
                }

                String lang = lit.getLanguage();
                if (lang == null || lang.isEmpty()) {
                    lang = DEFAULT_LANG;
                }

                Literal langStringLiteral = transformedModel.createLiteral(value, lang);
                toAdd.add(transformedModel.createStatement(
                        stmt.getSubject(),
                        SKOS.altLabel,
                        langStringLiteral
                ));
            }
            toRemove.add(stmt);
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);

        log.debug("Transformed {} alternative names to SKOS altLabel as rdf:langString", toAdd.size());
    }

    private void ensureDomainRangeProperties(OntModel transformedModel) {
        Property defOProperty = transformedModel.createProperty(effectiveNamespace + DEFINICNI_OBOR);
        if (transformedModel.listStatements(null, defOProperty, (RDFNode) null).hasNext()) {
            log.warn("Found custom definiční-obor properties");
            StmtIterator domainStmts = transformedModel.listStatements(null, defOProperty, (RDFNode) null);
            List<Statement> toAdd = new ArrayList<>();
            List<Statement> toRemove = new ArrayList<>();

            while (domainStmts.hasNext()) {
                Statement stmt = domainStmts.next();
                if (stmt.getObject().isResource()) {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            RDFS.domain,
                            stmt.getObject()
                    ));
                    toRemove.add(stmt);
                }
            }

            transformedModel.remove(toRemove);
            transformedModel.add(toAdd);
        }

        Property rangeProperty = transformedModel.createProperty(effectiveNamespace + OBOR_HODNOT);
        if (transformedModel.listStatements(null, rangeProperty, (RDFNode) null).hasNext()) {
            log.warn("Found custom obor-hodnot properties");
            StmtIterator rangeStmts = transformedModel.listStatements(null, rangeProperty, (RDFNode) null);
            List<Statement> toAdd = new ArrayList<>();
            List<Statement> toRemove = new ArrayList<>();

            while (rangeStmts.hasNext()) {
                Statement stmt = rangeStmts.next();
                if (stmt.getObject().isResource()) {
                    Resource rangeValue = stmt.getObject().asResource();
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            RDFS.range,
                            rangeValue
                    ));
                    toRemove.add(stmt);
                }
            }

            transformedModel.remove(toRemove);
            transformedModel.add(toAdd);
        }
    }

    private void mapCustomPropertiesToStandard(OntModel transformedModel) {
        String baseNamespace = DEFAULT_NS;
        String agendovyNamespace = baseNamespace + AGENDOVY_104;
        String legislativniNamespace = baseNamespace + LEGISLATIVNI_111;

        mapBooleanProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + JE_PPDF),
                transformedModel.createProperty(agendovyNamespace + JE_PPDF_LONG));

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + USTANOVENI_NEVEREJNOST),
                transformedModel.createProperty(legislativniNamespace + USTANOVENI_LONG));

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + AIS),
                transformedModel.createProperty(agendovyNamespace + UDAJE_AIS));

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + AGENDA),
                transformedModel.createProperty(agendovyNamespace + AGENDA_LONG));

        Property opisProperty = transformedModel.createProperty(effectiveNamespace + POPIS);
        StmtIterator opisStmts = transformedModel.listStatements(null, opisProperty, (RDFNode) null);
        List<Statement> toRemove = new ArrayList<>();

        while (opisStmts.hasNext()) {
            Statement stmt = opisStmts.next();
            if (isEmptyLiteralStatement(stmt)) {
                toRemove.add(stmt);
            }
        }

        transformedModel.remove(toRemove);
    }

    private void transformSourceProperties(OntModel transformedModel) {
        transformLegislativeSourceProperties(transformedModel);

        transformNonLegislativeSourceProperties(transformedModel);

        log.debug("Completed transformation of new source properties");
    }

    private void transformLegislativeSourceProperties(OntModel transformedModel) {
        Property definingProvisionProp = transformedModel.createProperty(effectiveNamespace + DEFINUJICI_USTANOVENI);
        Property definingProvisionDefaultProp = transformedModel.createProperty(DEFAULT_NS + DEFINUJICI_USTANOVENI);

        Property slovnikyDefiningProvision = transformedModel.createProperty(SLOVNIKY_NS + DEFINUJICI_USTANOVENI);
        mapProperty(transformedModel, definingProvisionProp, slovnikyDefiningProvision);
        mapProperty(transformedModel, definingProvisionDefaultProp, slovnikyDefiningProvision);

        Property relatedProvisionProp = transformedModel.createProperty(effectiveNamespace + SOUVISEJICI_USTANOVENI);
        Property relatedProvisionDefaultProp = transformedModel.createProperty(DEFAULT_NS + SOUVISEJICI_USTANOVENI);

        Property slovnikyRelatedProvision = transformedModel.createProperty(SLOVNIKY_NS + SOUVISEJICI_USTANOVENI);
        mapProperty(transformedModel, relatedProvisionProp, slovnikyRelatedProvision);
        mapProperty(transformedModel, relatedProvisionDefaultProp, slovnikyRelatedProvision);

        log.debug("Transformed legislative source properties to OFN slovníky namespace");
    }

    private void transformNonLegislativeSourceProperties(OntModel transformedModel) {
        String[] nonLegislativeProperties = {
                DEFINUJICI_NELEGISLATIVNI_ZDROJ,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ
        };

        for (String propName : nonLegislativeProperties) {
            Property customProp = transformedModel.createProperty(effectiveNamespace + propName);
            Property defaultProp = transformedModel.createProperty(DEFAULT_NS + propName);

            ensureDigitalDocumentUrls(transformedModel, customProp);
            ensureDigitalDocumentUrls(transformedModel, defaultProp);
        }

        log.debug("Processed non-legislative source properties and digital documents");
    }

    private void ensureDigitalDocumentUrls(OntModel transformedModel, Property sourceProperty) {
        StmtIterator stmts = transformedModel.listStatements(null, sourceProperty, (RDFNode) null);

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (stmt.getObject().isResource()) {
                Resource digitalDoc = stmt.getObject().asResource();

                Property schemaUrlProp = transformedModel.createProperty("http://schema.org/url");
                if (!digitalDoc.hasProperty(schemaUrlProp)) {
                    log.warn("Digital document missing schema:url property: {}", digitalDoc.getURI());
                }
            }
        }
    }

    private void mapBooleanProperty(OntModel model, Property sourceProperty, Property targetProperty) {
        StmtIterator stmts = model.listStatements(null, sourceProperty, (RDFNode) null);
        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (stmt.getObject().isLiteral()) {
                boolean value = false;
                String literal = stmt.getObject().asLiteral().getString().toLowerCase();
                if ("true".equals(literal) || "ano".equals(literal)) {
                    value = true;
                }
                toAdd.add(model.createStatement(
                        stmt.getSubject(),
                        targetProperty,
                        model.createTypedLiteral(value)
                ));
            }
            toRemove.add(stmt);
        }

        model.remove(toRemove);
        model.add(toAdd);
    }

    private void mapProperty(OntModel transformedModel, Property sourceProperty, Property targetProperty) {
        StmtIterator stmts = transformedModel.listStatements(null, sourceProperty, (RDFNode) null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (isEmptyLiteralStatement(stmt)) {
                toRemove.add(stmt);
                continue;
            }

            toAdd.add(transformedModel.createStatement(
                    stmt.getSubject(),
                    targetProperty,
                    stmt.getObject()
            ));
            toRemove.add(stmt);
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void addInSchemeRelationships(OntModel transformedModel) {
        String ontologyIRI = getOntologyIRI();
        if (ontologyIRI == null) {
            log.warn("Cannot add skos:inScheme relationships without ontology IRI");
            return;
        }

        Resource conceptScheme = transformedModel.getResource(ontologyIRI);
        if (conceptScheme == null) {
            log.warn("ConceptScheme resource not found: {}", ontologyIRI);
            return;
        }

        ResIterator conceptIter = transformedModel.listSubjectsWithProperty(RDF.type, SKOS.Concept);
        while (conceptIter.hasNext()) {
            Resource concept = conceptIter.next();

            if (!concept.hasProperty(RDF.type, OWL2.ObjectProperty) &&
                    !concept.hasProperty(RDF.type, OWL2.DatatypeProperty)) {
                concept.removeAll(SKOS.inScheme);
                concept.addProperty(SKOS.inScheme, conceptScheme);
            }
        }
    }

    private <T> T handleTurtleOperation(TurtleSupplier<T> operation) throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        try {
            return operation.get();
        } catch (TurtleExportException e) {
            log.error("Turtle export error: requestId={}, message={}", requestId, e.getMessage(), e);
            throw new TurtleExportException("Při exportu do Turtle došlo k chybě" + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during Turtle operation: requestId={}, error={}, type={}",
                    requestId, e.getMessage(), e.getClass().getName(), e);
            throw new TurtleExportException("Během zpracovávání Turtle došlo k chybě: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface TurtleSupplier<T> {
        T get() throws TurtleExportException;
    }
}