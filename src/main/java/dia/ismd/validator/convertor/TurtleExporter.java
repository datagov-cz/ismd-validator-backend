package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.TurtleExportException;
import dia.ismd.common.utility.UtilityMethods;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dia.ismd.validator.constants.ArchiOntologyConstants.*;
import static dia.ismd.validator.constants.ConvertorControllerConstants.*;

@Slf4j
class TurtleExporter {

    private final OntModel ontModel;
    @Getter
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;
    private final String effectiveNamespace;

    private static final Map<String, String> STANDARD_PREFIXES = new HashMap<>();

    static {
        STANDARD_PREFIXES.put("dct", DCTerms.getURI());
        STANDARD_PREFIXES.put("owl", OWL2.getURI());
        STANDARD_PREFIXES.put("rdf", RDF.getURI());
        STANDARD_PREFIXES.put("rdfs", RDFS.getURI());
        STANDARD_PREFIXES.put("skos", SKOS.getURI());
        STANDARD_PREFIXES.put("xsd", XSD);
    }

    public TurtleExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties) {
            this.ontModel = ontModel;
            this.resourceMap = new HashMap<>(resourceMap);
            this.modelName = modelName;
            this.modelProperties = modelProperties;
            this.effectiveNamespace = determineEffectiveNamespace();
    }

    public String exportToTurtle() throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export operation: requestId={}, modelName={}", requestId, modelName);
        return handleTurtleOperation(()-> {
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

    private OntModel createTransformedModel() {
        OntModel newModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        newModel.add(ontModel.listStatements());

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
    }

    private String determineEffectiveNamespace() {
        for (Map.Entry<String, String> entry : modelProperties.entrySet()) {
            if (entry.getKey().contains("adresa lokálního katalogu dat")) {
                String ns = entry.getValue();
                if (ns != null && !ns.isEmpty() && UtilityMethods.isValidUrl(ns)) {
                    return UtilityMethods.ensureNamespaceEndsWithDelimiter(ns);
                }
            }
        }

        return NS;
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
            return "domain";
        }

        String domain = namespace
                .replaceAll("https?://", "")
                .replace("www\\.", "");

        String[] parts = domain.split("[./]");

        for (String part : parts) {
            if (!part.isEmpty()) {
                return part.toLowerCase();
            }
        }

        return "domain";
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
            resource.addProperty(SKOS.prefLabel, modelName, "cs");
        }

        String description = modelProperties.getOrDefault(LABEL_POPIS, "");
        if (description != null && !description.isEmpty()) {
            resource.removeAll(DCTerms.description);
            resource.addProperty(DCTerms.description, description, "cs");
        }
    }

    private void transformResourcesToSKOSConcepts(OntModel transformedModel) {
        Resource pojemType = transformedModel.createResource(effectiveNamespace + TYP_POJEM);
        ResIterator pojemResources = transformedModel.listSubjectsWithProperty(RDF.type, pojemType);

        while (pojemResources.hasNext()) {
            Resource resource = pojemResources.next();

            resource.addProperty(RDF.type, SKOS.Concept);

            mapResourceTypes(resource, transformedModel);
        }
    }

    private void mapResourceTypes(Resource resource, OntModel transformedModel) {
        String baseNamespace = NS;
        String verejnySektorNamespace = baseNamespace + "veřejný-sektor/pojem/";

        if (hasResourceType(resource, transformedModel, TYP_TRIDA)) {
            addResourceType(resource, OWL2.Class);
            mapSubjectOrObjectType(resource, transformedModel, verejnySektorNamespace);
        }
        else if (hasResourceType(resource, transformedModel, TYP_VLASTNOST)) {
            mapPropertyType(resource, transformedModel);
        }
        else if (hasResourceType(resource, transformedModel, TYP_VZTAH)) {
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
        if (hasResourceType(resource, model, TYP_TSP)) {
            addResourceType(resource, model.createResource(vsNamespace + "typ-subjektu-práva"));
        } else if (hasResourceType(resource, model, TYP_TOP)) {
            addResourceType(resource, model.createResource(vsNamespace + "typ-objektu-práva"));
        }
    }

    private void mapPropertyType(Resource resource, OntModel model) {
        Property rangeProperty = model.createProperty(effectiveNamespace + LABEL_OBOR_HODNOT);
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
        if (hasResourceType(resource, model, TYP_VEREJNY_UDAJ)) {
            String publicDataUri = baseNamespace + "legislativní/sbírka/111/2009/pojem/veřejný-údaj";
            addResourceType(resource, model.createResource(publicDataUri));
        } else if (hasResourceType(resource, model, TYP_NEVEREJNY_UDAJ)) {
            String nonPublicDataUri = baseNamespace + "legislativní/sbírka/111/2009/pojem/neveřejný-údaj";
            addResourceType(resource, model.createResource(nonPublicDataUri));
        }
    }

    private void transformLabelsToSKOS(OntModel transformedModel) {
        Map<Resource, Map<String, String>> resourceLabels = new HashMap<>();

        StmtIterator labelStmts = transformedModel.listStatements(null, RDFS.label, (RDFNode)null);
        while (labelStmts.hasNext()) {
            Statement stmt = labelStmts.next();
            if (stmt.getObject().isLiteral()) {
                Resource subject = stmt.getSubject();
                Literal lit = stmt.getObject().asLiteral();
                String lang = lit.getLanguage();
                String text = lit.getString();

                if (lang == null || lang.isEmpty()) {
                    lang = "cs";
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
        Property defProperty = transformedModel.createProperty(effectiveNamespace + LABEL_DEF);
        StmtIterator defStmts = transformedModel.listStatements(null, defProperty, (RDFNode)null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (defStmts.hasNext()) {
            Statement stmt = defStmts.next();
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                String lang = lit.getLanguage();

                if (lang != null && !lang.isEmpty()) {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            SKOS.definition,
                            transformedModel.createLiteral(lit.getString(), lang)
                    ));
                } else {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            SKOS.definition,
                            transformedModel.createLiteral(lit.getString(), "cs")
                    ));
                }
            }
            toRemove.add(stmt);
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void ensureDomainRangeProperties(OntModel transformedModel) {
        Property defOProperty = transformedModel.createProperty(effectiveNamespace + LABEL_DEF_O);
        StmtIterator domainStmts = transformedModel.listStatements(null, defOProperty, (RDFNode)null);

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

        Property rangeProperty = transformedModel.createProperty(effectiveNamespace + LABEL_OBOR_HODNOT);
        StmtIterator rangeStmts = transformedModel.listStatements(null, rangeProperty, (RDFNode)null);

        toAdd = new ArrayList<>();
        toRemove = new ArrayList<>();

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

    private void mapCustomPropertiesToStandard(OntModel transformedModel) {
        String baseNamespace = "https://slovník.gov.cz/";
        String agendovyNamespace = baseNamespace + "agendový/104/pojem/";
        String legislativniNamespace = baseNamespace + "legislativní/sbírka/111/2009/pojem/";

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + LABEL_ZDROJ),
                DCTerms.relation);

        mapBooleanProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + LABEL_JE_PPDF),
                transformedModel.createProperty(agendovyNamespace + "je-sdílen-v-propojeném-datovém-fondu"));

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + LABEL_SUPP),
                transformedModel.createProperty(legislativniNamespace + "je-vymezen-ustanovení-stanovujícím-jeho-neveřejnost"));

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + LABEL_NT),
                RDFS.subClassOf);

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + LABEL_AIS),
                transformedModel.createProperty(agendovyNamespace + "údaje-jsou-v-ais"));

        mapProperty(transformedModel,
                transformedModel.createProperty(effectiveNamespace + LABEL_AGENDA),
                transformedModel.createProperty(agendovyNamespace + "sdružuje-údaje-vedené-nebo-vytvářené-v-rámci-agendy"));
    }

    private void mapBooleanProperty(OntModel model, Property sourceProperty, Property targetProperty) {
        StmtIterator stmts = model.listStatements(null, sourceProperty, (RDFNode)null);
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
        StmtIterator stmts = transformedModel.listStatements(null, sourceProperty, (RDFNode)null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
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

    private <T> T handleTurtleOperation(TurtleExporter.TurtleSupplier<T> operation) throws TurtleExportException {
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
