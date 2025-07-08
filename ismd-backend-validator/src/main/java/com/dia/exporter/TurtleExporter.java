package com.dia.exporter;

import com.dia.exceptions.TurtleExportException;
import com.dia.utility.Transliterator;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.next();
            if (!isEmptyLiteralStatement(stmt)) {
                newModel.add(stmt);
            } else {
                log.debug("Filtering out empty literal statement: {}", stmt);
            }
        }

        return newModel;
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

        String prefix = determineMainPrefix(effectiveNamespace);
        transformedModel.setNsPrefix(prefix, effectiveNamespace);
    }

    private String determineMainPrefix(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return DEFAULT_PREFIX;
        }

        log.debug("Determining prefix for namespace: {}", namespace);

        String domain = namespace
                .replaceAll("^https?://", "")
                .replaceAll("^www\\.", "");

        if (domain.startsWith("urn:")) {
            String[] urnParts = domain.split(":");
            if (urnParts.length > 1) {
                domain = urnParts[1];
            }
        }

        String[] parts = domain.split("[./:#-]");

        for (String part : parts) {
            if (!part.isEmpty()) {
                log.debug("Processing domain part: '{}'", part);

                String transliterated = Transliterator.transliterate(part);
                log.debug("After transliteration: '{}'", transliterated);

                String cleaned = UtilityMethods.cleanForXMLName(transliterated);
                log.debug("After XML cleaning: '{}'", cleaned);

                if (UtilityMethods.isValidXMLNameStart(cleaned)) {
                    String result = cleaned.toLowerCase();
                    log.debug("Generated prefix: '{}'", result);
                    return result;
                }
            }
        }

        log.debug("Falling back to default prefix: {}", DEFAULT_PREFIX);
        return DEFAULT_PREFIX;
    }

    private void createConceptScheme(OntModel transformedModel) {
        String ontologyUri = effectiveNamespace;
        if (ontologyUri.endsWith("/") || ontologyUri.endsWith("#")) {
            ontologyUri = ontologyUri.substring(0, ontologyUri.length() - 1);
        }

        Resource resource;
        StmtIterator iter = transformedModel.listStatements(null, RDF.type, OWL2.Ontology);
        if (iter.hasNext()) {
            resource = iter.next().getSubject();
        } else {
            resource = transformedModel.createResource(ontologyUri);
            resource.addProperty(RDF.type, OWL2.Ontology);
        }

        resource.addProperty(RDF.type, SKOS.ConceptScheme);

        if (modelName != null && !modelName.isEmpty()) {
            resource.removeAll(SKOS.prefLabel);
            resource.addProperty(SKOS.prefLabel, modelName, DEFAULT_LANG);
        }

        String description = modelProperties.getOrDefault(POPIS, "");
        if (description != null && !description.isEmpty()) {
            resource.removeAll(DCTerms.description);
            resource.addProperty(DCTerms.description, description, DEFAULT_LANG);
        }
    }

    private void transformResourcesToSKOSConcepts(OntModel transformedModel) {
        Resource pojemType = transformedModel.createResource(effectiveNamespace + POJEM);
        ResIterator pojemResources = transformedModel.listSubjectsWithProperty(RDF.type, pojemType);

        while (pojemResources.hasNext()) {
            Resource resource = pojemResources.next();

            resource.addProperty(RDF.type, SKOS.Concept);

            mapResourceTypes(resource, transformedModel);
        }
    }

    private void mapResourceTypes(Resource resource, OntModel transformedModel) {
        String baseNamespace = DEFAULT_NS;
        String verejnySektorNamespace = baseNamespace + VS_POJEM;

        if (hasResourceType(resource, transformedModel, TRIDA)) {
            addResourceType(resource, OWL2.Class);
            mapSubjectOrObjectType(resource, transformedModel, verejnySektorNamespace);
        } else if (hasResourceType(resource, transformedModel, VLASTNOST)) {
            mapPropertyType(resource, transformedModel);
        } else if (hasResourceType(resource, transformedModel, VZTAH)) {
            addResourceType(resource, OWL2.ObjectProperty);
        }

        mapDataAccessType(resource, transformedModel, baseNamespace);
    }

    private boolean hasResourceType(Resource resource, OntModel model, String typeName) {
        Resource typeResource = model.createResource(effectiveNamespace + typeName);
        return resource.hasProperty(RDF.type, typeResource);
    }

    private void addResourceType(Resource resource, Resource type) {
        resource.addProperty(RDF.type, type);
    }

    private void mapSubjectOrObjectType(Resource resource, OntModel model, String vsNamespace) {
        if (hasResourceType(resource, model, TSP)) {
            addResourceType(resource, model.createResource(vsNamespace + TSP));
        } else if (hasResourceType(resource, model, TOP)) {
            addResourceType(resource, model.createResource(vsNamespace + TOP));
        }
    }

    private void mapPropertyType(Resource resource, OntModel model) {
        Property rangeProperty = model.createProperty(effectiveNamespace + OBOR_HODNOT);
        Statement rangeStmt = resource.getProperty(rangeProperty);

        if (rangeStmt != null && rangeStmt.getObject().isResource()) {
            String rangeUri = rangeStmt.getObject().asResource().getURI();
            if (rangeUri.startsWith(XSD)) {
                addResourceType(resource, OWL2.DatatypeProperty);
            } else {
                addResourceType(resource, OWL2.ObjectProperty);
            }
        } else {
            addResourceType(resource, OWL2.DatatypeProperty);
        }
    }

    private void mapDataAccessType(Resource resource, OntModel model, String baseNamespace) {
        if (hasResourceType(resource, model, VEREJNY_UDAJ)) {
            String publicDataUri = baseNamespace + LEGISLATIVNI_111_VU;
            addResourceType(resource, model.createResource(publicDataUri));
        } else if (hasResourceType(resource, model, NEVEREJNY_UDAJ)) {
            String nonPublicDataUri = baseNamespace + LEGISLATIVNI_111_NVU;
            addResourceType(resource, model.createResource(nonPublicDataUri));
        }
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
        Resource conceptScheme;
        StmtIterator iter = transformedModel.listStatements(null, RDF.type, SKOS.ConceptScheme);
        if (iter.hasNext()) {
            conceptScheme = iter.next().getSubject();
        } else {
            return;
        }

        ResIterator conceptIter = transformedModel.listSubjectsWithProperty(RDF.type, SKOS.Concept);
        while (conceptIter.hasNext()) {
            Resource concept = conceptIter.next();
            concept.removeAll(SKOS.inScheme);
            concept.addProperty(SKOS.inScheme, conceptScheme);
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