package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ExportException;
import dia.ismd.common.exceptions.TurtleExportException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dia.ismd.validator.constants.ArchiOntologyConstants.*;

@Slf4j
class TurtleExporter {
    // TODO potřebuje refactor

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
        STANDARD_PREFIXES.put("xsd", "http://www.w3.org/2001/XMLSchema#");
    }

    public TurtleExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties) {
            this.ontModel = ontModel;
            this.resourceMap = new HashMap<>(resourceMap);
            this.modelName = modelName;
            this.modelProperties = modelProperties;
            this.effectiveNamespace = determineEffectiveNamespace();
    }

    public String exportToTurtle() throws TurtleExportException {
        try {
            OntModel transformedModel = createTransformedModel();

            applyTransformations(transformedModel);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            RDFDataMgr.write(outputStream, transformedModel, RDFFormat.TURTLE_PRETTY);

            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (TurtleExportException e) {
            log.error("Error exporting to Turtle format: {}", e.getMessage(), e);
            throw new TurtleExportException("Při exportu do Turtle došlo k chybě: " + e.getMessage());
        }
    }

    public String exportToTurtlePretty(boolean prettyPrint, boolean includeBaseUri) {
        try {
            OntModel transformedModel = createTransformedModel();

            applyTransformations(transformedModel);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            RDFFormat format = prettyPrint ? RDFFormat.TURTLE_PRETTY : RDFFormat.TURTLE_BLOCKS;
            RDFDataMgr.write(outputStream, transformedModel, format);

            String output = outputStream.toString(StandardCharsets.UTF_8);

            if (!includeBaseUri) {
                output = output.replaceAll("@base\\s+<[^>]*>\\s*\\.", "");
            }

            return output;
        } catch (ExportException e) {
            log.error("Error exporting to Turtle format: {}", e.getMessage(), e);
            throw new ExportException("Error exporting to Turtle format: " + e.getMessage());
        }
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
                if (ns != null && !ns.isEmpty() && isValidUrl(ns)) {
                    return ensureNamespaceEndsWithDelimiter(ns);
                }
            }
        }

        StmtIterator iter = ontModel.listStatements(null, RDF.type, OWL2.Ontology);
        if (iter.hasNext()) {
            String uri = iter.next().getSubject().getURI();
            if (uri != null && !uri.isEmpty()) {
                int lastHash = uri.lastIndexOf('#');
                int lastSlash = uri.lastIndexOf('/');
                int pos = Math.max(lastHash, lastSlash);

                if (pos > 0) {
                    return uri.substring(0, pos + 1);
                }
            }
        }

        return NS;
    }

    private String ensureNamespaceEndsWithDelimiter(String namespace) {
        if (!namespace.endsWith("/") && !namespace.endsWith("#")) {
            return namespace + "/";
        }
        return namespace;
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
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
                .replaceAll("www\\.", "");

        String[] parts = domain.split("[./]");

        for (String part : parts) {
            if (!part.isEmpty() && !isTLD(part)) {
                return part.toLowerCase();
            }
        }

        return "domain";
    }

    private boolean isTLD(String part) {
        String[] commonTLDs = {"com", "org", "net", "gov", "cz", "eu", "io"};
        for (String tld : commonTLDs) {
            if (tld.equals(part)) {
                return true;
            }
        }
        return false;
    }

    private void createConceptScheme(OntModel transformedModel) {
        String ontologyUri = effectiveNamespace;
        if (ontologyUri.endsWith("/") || ontologyUri.endsWith("#")) {
            ontologyUri = ontologyUri.substring(0, ontologyUri.length() - 1);
        }

        Resource resource = null;
        StmtIterator iter = transformedModel.listStatements(null, RDF.type, OWL2.Ontology);
        if (iter.hasNext()) {
            resource = iter.next().getSubject();
        } else {
            resource = transformedModel.createResource(ontologyUri);
        }

        resource.addProperty(RDF.type, OWL2.Ontology);
        resource.addProperty(RDF.type, SKOS.ConceptScheme);

        if (modelName != null && !modelName.isEmpty()) {
            resource.removeAll(SKOS.prefLabel);

            resource.addProperty(SKOS.prefLabel, modelName, "cs");
        }

        String description = modelProperties.getOrDefault(LABEL_POPIS, "");
        if (description != null && !description.isEmpty()) {
            resource.removeAll(DCTerms.description);

            resource.addProperty(DCTerms.description, description, "cs");

            String englishDescription = modelProperties.getOrDefault("popis-en", "");
            if (englishDescription != null && !englishDescription.isEmpty()) {
                resource.addProperty(DCTerms.description, englishDescription, "en");
            }
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
        if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_TRIDA))) {
            resource.addProperty(RDF.type, OWL2.Class);

            if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_TSP))) {
                resource.addProperty(RDF.type, transformedModel.createResource(effectiveNamespace.replace("sbírka/56/2001", "veřejný-sektor") + "typ-subjektu-práva"));
            } else if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_TOP))) {
                resource.addProperty(RDF.type, transformedModel.createResource(effectiveNamespace.replace("sbírka/56/2001", "veřejný-sektor") + "typ-objektu-práva"));
            }
        }
        else if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_VLASTNOST))) {
            Property rangeProperty = transformedModel.createProperty(effectiveNamespace + LABEL_OBOR_HODNOT);
            Statement rangeStmt = resource.getProperty(rangeProperty);

            if (rangeStmt != null && rangeStmt.getObject().isResource()) {
                String rangeUri = rangeStmt.getObject().asResource().getURI();
                if (rangeUri.startsWith("http://www.w3.org/2001/XMLSchema#")) {
                    resource.addProperty(RDF.type, OWL2.DatatypeProperty);
                } else {
                    resource.addProperty(RDF.type, OWL2.ObjectProperty);
                }
            } else {
                resource.addProperty(RDF.type, OWL2.DatatypeProperty);
            }
        }
        else if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_VZTAH))) {
            resource.addProperty(RDF.type, OWL2.ObjectProperty);
        }

        if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_VEREJNY_UDAJ))) {
            resource.addProperty(RDF.type, transformedModel.createResource(effectiveNamespace.replace("sbírka/56/2001", "sbírka/111/2009") + "veřejný-údaj"));
        } else if (resource.hasProperty(RDF.type, transformedModel.createResource(effectiveNamespace + TYP_NEVEREJNY_UDAJ))) {
            resource.addProperty(RDF.type, transformedModel.createResource(effectiveNamespace.replace("sbírka/56/2001", "sbírka/111/2009") + "neveřejný-údaj"));
        }
    }

    private void transformLabelsToSKOS(OntModel transformedModel) {
        StmtIterator labelStmts = transformedModel.listStatements(null, RDFS.label, (RDFNode)null);
        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (labelStmts.hasNext()) {
            Statement stmt = labelStmts.next();
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                String lang = lit.getLanguage();

                if (lang != null && !lang.isEmpty()) {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            SKOS.prefLabel,
                            transformedModel.createLiteral(lit.getString(), lang)
                    ));
                } else {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            SKOS.prefLabel,
                            transformedModel.createLiteral(lit.getString(), "cs")
                    ));
                }
            }
            toRemove.add(stmt);
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);

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
                String rangeUri = rangeValue.getURI();

                if (rangeUri.startsWith("http://www.w3.org/2001/XMLSchema#")) {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            RDFS.range,
                            rangeValue
                    ));
                } else {
                    toAdd.add(transformedModel.createStatement(
                            stmt.getSubject(),
                            RDFS.range,
                            rangeValue
                    ));
                }
                toRemove.add(stmt);
            }
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void mapCustomPropertiesToStandard(OntModel transformedModel) {
        mapPropertyToDCTerms(transformedModel);

        mapPpdfSharingProperty(transformedModel);

        mapNonPublicDataProvisions(transformedModel);

        mapSubclassRelationships(transformedModel);

        mapAgendaAndSystemProperties(transformedModel);
    }

    private void mapPropertyToDCTerms(OntModel transformedModel) {
        Property sourceProperty = transformedModel.createProperty(effectiveNamespace + dia.ismd.validator.constants.ArchiOntologyConstants.LABEL_ZDROJ);
        Property targetProperty = transformedModel.createProperty(DCTerms.getURI() + "relation");

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

    private void mapPpdfSharingProperty(OntModel transformedModel) {
        Property ppdfProperty = transformedModel.createProperty(effectiveNamespace + LABEL_JE_PPDF);
        Property targetProperty = transformedModel.createProperty(effectiveNamespace.replace("sbírka/56/2001", "agendový/104") + LABEL_JE_PPDF);

        StmtIterator stmts = transformedModel.listStatements(null, ppdfProperty, (RDFNode)null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (stmt.getObject().isLiteral()) {
                boolean value = stmt.getObject().asLiteral().getBoolean();
                toAdd.add(transformedModel.createStatement(
                        stmt.getSubject(),
                        targetProperty,
                        transformedModel.createTypedLiteral(value)
                ));
            }
            toRemove.add(stmt);
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void mapNonPublicDataProvisions(OntModel transformedModel) {
        Property suppProperty = transformedModel.createProperty(effectiveNamespace + LABEL_SUPP);
        Property targetProperty = transformedModel.createProperty(effectiveNamespace.replace("sbírka/56/2001", "sbírka/111/2009")
                + "je-vymezen-ustanovení-stanovujícím-jeho-neveřejnost");

        StmtIterator stmts = transformedModel.listStatements(null, suppProperty, (RDFNode)null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (stmt.getObject().isResource()) {
                toAdd.add(transformedModel.createStatement(
                        stmt.getSubject(),
                        targetProperty,
                        stmt.getObject()
                ));
                toRemove.add(stmt);
            }
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void mapSubclassRelationships(OntModel transformedModel) {
        Property ntProperty = transformedModel.createProperty(effectiveNamespace + LABEL_NT);

        StmtIterator stmts = transformedModel.listStatements(null, ntProperty, (RDFNode)null);

        List<Statement> toAdd = new ArrayList<>();
        List<Statement> toRemove = new ArrayList<>();

        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            if (stmt.getObject().isResource()) {
                toAdd.add(transformedModel.createStatement(
                        stmt.getSubject(),
                        RDFS.subClassOf,
                        stmt.getObject()
                ));
                toRemove.add(stmt);
            }
        }

        transformedModel.remove(toRemove);
        transformedModel.add(toAdd);
    }

    private void mapAgendaAndSystemProperties(OntModel transformedModel) {
        Property aisProperty = transformedModel.createProperty(effectiveNamespace + LABEL_AIS);
        Property targetAisProperty = transformedModel.createProperty(effectiveNamespace
                .replace("sbírka/56/2001", "agendový/104") + "údaje-jsou-v-ais");

        mapProperty(transformedModel, aisProperty, targetAisProperty);

        Property agendaProperty = transformedModel.createProperty(effectiveNamespace + LABEL_AGENDA);
        Property targetAgendaProperty = transformedModel.createProperty(effectiveNamespace
                .replace("sbírka/56/2001", "agendový/104")
                + "sdružuje-údaje-vedené-nebo-vytvářené-v-rámci-agendy");

        mapProperty(transformedModel, agendaProperty, targetAgendaProperty);
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
        Resource conceptScheme = null;
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
}
