package com.dia.exporter;

import com.dia.exceptions.TurtleExportException;
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

import static com.dia.constants.ArchiConstants.*;
import static com.dia.constants.ExportConstants.Turtle.*;
import static com.dia.constants.ExportConstants.Common.*;
import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;

@Slf4j
public class TurtleExporter {

    private final OntModel ontModel;
    @Getter
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;
    private final String effectiveNamespace;

    private static final Map<String, String> STANDARD_PREFIXES = new HashMap<>();

    static {
        STANDARD_PREFIXES.put(PREFIX_DCT, DCTerms.getURI());
        STANDARD_PREFIXES.put(PREFIX_OWL, OWL2.getURI());
        STANDARD_PREFIXES.put(PREFIX_RDF, RDF.getURI());
        STANDARD_PREFIXES.put(PREFIX_RDFS, RDFS.getURI());
        STANDARD_PREFIXES.put(PREFIX_SKOS, SKOS.getURI());
        STANDARD_PREFIXES.put(PREFIX_XSD, XSD);
        STANDARD_PREFIXES.put("vsgov", "https://slovník.gov.cz/veřejný-sektor/pojem/");
        STANDARD_PREFIXES.put("l111-2009", "https://slovník.gov.cz/legislativní/sbírka/111/2009/pojem/");
        STANDARD_PREFIXES.put("a104", "https://slovník.gov.cz/agendový/104/pojem/");
        STANDARD_PREFIXES.put("slovníky", "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/");
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

            if (isEmptyLiteralStatement(stmt)) {
                filteredStatements++;
                log.debug("Filtering out empty literal statement: {}", stmt);
                continue;
            }

            Resource subject = stmt.getSubject();
            if (isBaseSchemaResource(subject.getURI())) {
                filteredStatements++;
                log.debug("Filtering out base schema definition: {}", stmt);
                continue;
            }

            newModel.add(stmt);
        }

        log.debug("Model transformation: {} original statements, {} filtered out, {} retained",
                originalStatements, filteredStatements, originalStatements - filteredStatements);

        return newModel;
    }

    private boolean isBaseSchemaResource(String uri) {
        if (uri == null) return false;

        if (uri.startsWith("http://www.w3.org/2001/XMLSchema#")) {
            return true;
        }

        if (uri.startsWith("https://slovník.gov.cz/")) {
            return !uri.contains("/legislativní/") &&
                    !uri.contains("/agendový/") &&
                    !uri.contains("/veřejný-sektor/");
        }

        return false;
    }

    private void applyTransformations(OntModel transformedModel) {
        setupPrefixes(transformedModel);

        createConceptScheme(transformedModel);

        transformResourcesToSKOSConcepts(transformedModel);

        transformLabelsToSKOS(transformedModel);

        ensureDomainRangeProperties(transformedModel);

        mapCustomPropertiesToStandard(transformedModel);

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

    private void transformResourcesToSKOSConcepts(OntModel transformedModel) {
        Resource ofnPojemType = transformedModel.createResource(OFN_NAMESPACE + POJEM);
        ResIterator pojemResources = transformedModel.listSubjectsWithProperty(RDF.type, ofnPojemType);

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

        int conceptCount = 0;
        int filteredCount = 0;

        while (pojemResources.hasNext()) {
            Resource resource = pojemResources.next();

            if (baseSchemaClasses.contains(resource.getURI())) {
                filteredCount++;
                log.debug("Filtering out base schema class from SKOS concepts: {}", resource.getURI());
                continue;
            }

            conceptCount++;

            if (!resource.hasProperty(RDF.type, SKOS.Concept)) {
                resource.addProperty(RDF.type, SKOS.Concept);
            }

            log.debug("Processing OFN pojem as SKOS concept: {}", resource.getURI());
        }

        log.debug("Found and processed {} OFN pojmy as SKOS concepts, filtered out {} base schema classes",
                conceptCount, filteredCount);
    }

    private void transformLabelsToSKOS(OntModel transformedModel) {
        Map<Resource, Map<String, String>> resourceLabels = new HashMap<>();

        StmtIterator labelStmts = transformedModel.listStatements(null, RDFS.label, (RDFNode) null);
        while (labelStmts.hasNext()) {
            Statement stmt = labelStmts.next();
            if (stmt.getObject().isLiteral()) {
                Resource subject = stmt.getSubject();
                Literal lit = stmt.getObject().asLiteral();
                String lang = lit.getLanguage();
                String text = lit.getString();

                if (text == null || text.isEmpty()) {
                    continue;
                }

                if (lang == null || lang.isEmpty()) {
                    lang = DEFAULT_LANG;
                }

                resourceLabels.computeIfAbsent(subject, k -> new HashMap<>()).put(lang, text);
            }
        }

        transformedModel.removeAll(null, RDFS.label, null);

        for (Map.Entry<Resource, Map<String, String>> entry : resourceLabels.entrySet()) {
            Resource subject = entry.getKey();
            Map<String, String> labels = entry.getValue();

            for (Map.Entry<String, String> langLabel : labels.entrySet()) {
                subject.addProperty(SKOS.prefLabel,
                        transformedModel.createLiteral(langLabel.getValue(), langLabel.getKey()));
            }
        }

        transformDefinitionsToSKOS(transformedModel);
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

    private void ensureDomainRangeProperties(OntModel transformedModel) {
        Property defOProperty = transformedModel.createProperty(effectiveNamespace + DEFINICNI_OBOR);
        if (transformedModel.listStatements(null, defOProperty, (RDFNode) null).hasNext()) {
            log.warn("Found custom definiční-obor properties - these should have been rdfs:domain from the start");
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
            log.warn("Found custom obor-hodnot properties - these should have been rdfs:range from the start");
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
        int conceptCount = 0;
        while (conceptIter.hasNext()) {
            Resource concept = conceptIter.next();
            concept.removeAll(SKOS.inScheme);
            concept.addProperty(SKOS.inScheme, conceptScheme);
            conceptCount++;
            log.debug("Added skos:inScheme: {} -> {}", concept.getURI(), conceptScheme.getURI());
        }

        log.debug("Added skos:inScheme relationships for {} concepts to full IRI: {}", conceptCount, ontologyIRI);
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