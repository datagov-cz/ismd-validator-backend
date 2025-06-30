package com.dia.converter.transformer;

import com.dia.converter.data.*;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import com.dia.exporter.JsonExporter;
import com.dia.exporter.TurtleExporter;
import com.dia.models.OFNBaseModel;
import com.dia.utility.DataTypeConverter;
import com.dia.utility.URIGenerator;
import com.dia.utility.UtilityMethods;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.dia.constants.ArchiConstants.*;
import static com.dia.constants.ArchiConstants.AGENDA;
import static com.dia.constants.ArchiConstants.AIS;
import static com.dia.constants.ArchiConstants.DEFINICE;
import static com.dia.constants.ArchiConstants.IDENTIFIKATOR;
import static com.dia.constants.ArchiConstants.JE_PPDF;
import static com.dia.constants.ArchiConstants.NAZEV;
import static com.dia.constants.ArchiConstants.POPIS;
import static com.dia.constants.ArchiConstants.SOUVISEJICI_ZDROJ;
import static com.dia.constants.ArchiConstants.ZDROJ;
import static com.dia.constants.ExcelConstants.*;
import static com.dia.constants.ExportConstants.Common.*;
import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;

/**
 * OFNDataTransformer - Transforms parsed data from Excel and EA ontologies into OFN ontology models
 */
@Component
@Slf4j
@Getter
public class OFNDataTransformer {

    private final OFNBaseModel baseModel;
    private final OntModel ontModel;
    private final Map<String, Resource> resourceMap;
    private final URIGenerator uriGenerator;

    private final Map<String, Resource> classResources = new HashMap<>();
    private final Map<String, Resource> propertyResources = new HashMap<>();
    private final Map<String, Resource> relationshipResources = new HashMap<>();

    @Setter
    private String modelName;

    @Setter
    private Boolean removeELI;

    public OFNDataTransformer() {
        this.baseModel = new OFNBaseModel();
        this.ontModel = baseModel.getOntModel();
        this.resourceMap = new HashMap<>();
        this.uriGenerator = new URIGenerator();
    }

    public TransformationResult transform(OntologyData ontologyData) throws ConversionException {
        try {
            log.info("Starting ontology data transformation...");

            if (ontologyData == null || ontologyData.getVocabularyMetadata() == null) {
                throw new ConversionException("Invalid ontology data");
            }

            String effectiveNamespace = determineEffectiveNamespace(ontologyData.getVocabularyMetadata());
            uriGenerator.setEffectiveNamespace(effectiveNamespace);

            log.debug("Using effective namespace: {}", effectiveNamespace);
            log.debug("Model name: {}", ontologyData.getVocabularyMetadata().getName());

            initializeCleanTypeClasses();
            createOntologyResource(ontologyData.getVocabularyMetadata(), effectiveNamespace);

            transformClasses(ontologyData.getClasses());
            transformProperties(ontologyData.getProperties());
            transformRelationships(ontologyData.getRelationships());
            transformHierarchies(ontologyData.getHierarchies());

            Map<String, String> modelProperties = createModelProperties(ontologyData.getVocabularyMetadata());

            log.info("Transformation completed successfully. Created {} classes, {} properties, {} relationships",
                    classResources.size(), propertyResources.size(), relationshipResources.size());

            return new TransformationResult(
                    ontModel,
                    new HashMap<>(resourceMap),
                    ontologyData.getVocabularyMetadata().getName(),
                    modelProperties,
                    effectiveNamespace
            );

        } catch (Exception e) {
            log.error("Transformation failed", e);
            throw new ConversionException("Failed to transform ontology data", e);
        }
    }

    public String exportToJson(TransformationResult transformationResult) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting JSON export: requestId={}, modelName={}", requestId, modelName);

        try {
            JsonExporter exporter = new JsonExporter(
                    transformationResult.getOntModel(),
                    transformationResult.getResourceMap(),
                    transformationResult.getModelName(),
                    transformationResult.getModelProperties(),
                    transformationResult.getEffectiveNamespace()
            );
            String result = exporter.exportToJson();
            log.info("JSON export completed: requestId={}, outputSize={}", requestId, result.length());
            return result;
        } catch (Exception e) {
            log.error("Unexpected error during JSON export: requestId={}", requestId, e);
            throw new JsonExportException("Neočekávaná chyba při exportu do JSON.", e);
        }
    }

    public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}, modelName={}", requestId, modelName);

        try {
            TurtleExporter exporter = new TurtleExporter(
                    transformationResult.getOntModel(),
                    transformationResult.getResourceMap(),
                    transformationResult.getModelName(),
                    transformationResult.getModelProperties(),
                    transformationResult.getEffectiveNamespace()
            );
            String result = exporter.exportToTurtle();
            log.info("Turtle export completed: requestId={}, outputSize={}", requestId, result.length());
            return result;
        } catch (Exception e) {
            log.error("Unexpected error during Turtle export: requestId={}", requestId, e);
            throw new TurtleExportException("Neočekávaná chyba při exportu do Turtle.", e);
        }
    }

    private void createOntologyResource(VocabularyMetadata metadata, String effectiveNamespace) {
        String ontologyIRI = assembleOntologyIRI(metadata.getName(), effectiveNamespace);
        log.debug("Creating ontology resource with IRI: {}", ontologyIRI);

        ontModel.createOntology(ontologyIRI);
        Resource ontologyResource = ontModel.getResource(ontologyIRI);

        if (ontologyResource != null) {
            ontologyResource.addProperty(RDF.type, ontModel.getResource("http://www.w3.org/2002/07/owl#Ontology"));
            ontologyResource.addProperty(RDF.type, SKOS.ConceptScheme);

            if (metadata.getName() != null && !metadata.getName().trim().isEmpty()) {
                ontologyResource.addProperty(SKOS.prefLabel, metadata.getName(), DEFAULT_LANG);
            }

            if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
                Property descProperty = ontModel.createProperty("http://purl.org/dc/terms/description");
                DataTypeConverter.addTypedProperty(ontologyResource, descProperty, metadata.getDescription(), DEFAULT_LANG, ontModel);
            }

            resourceMap.put("ontology", ontologyResource);
            log.debug("Ontology resource created successfully: {}", ontologyIRI);
        }
    }

    private void initializeCleanTypeClasses() {
        String namespace = uriGenerator.getEffectiveNamespace();

        ontModel.createResource(namespace + POJEM);
        ontModel.createResource(namespace + TRIDA);
        ontModel.createResource(namespace + VZTAH);
        ontModel.createResource(namespace + VLASTNOST);
        ontModel.createResource(namespace + TSP);
        ontModel.createResource(namespace + TOP);
        ontModel.createResource(namespace + VEREJNY_UDAJ);
        ontModel.createResource(namespace + NEVEREJNY_UDAJ);

        log.debug("Initialized clean type classes with hyphenated URIs");
    }

    private String assembleOntologyIRI(String modelName, String effectiveNamespace) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return effectiveNamespace;
        }
        String baseNamespace = effectiveNamespace;
        if (baseNamespace.endsWith("/")) {
            baseNamespace = baseNamespace.substring(0, baseNamespace.length() - 1);
        }
        return baseNamespace + "/" + UtilityMethods.sanitizeForIRI(modelName);
    }

    private String determineEffectiveNamespace(VocabularyMetadata metadata) {
        String namespace = metadata.getNamespace();
        if (namespace != null && !namespace.trim().isEmpty() && UtilityMethods.isValidUrl(namespace)) {
            return UtilityMethods.ensureNamespaceEndsWithDelimiter(namespace);
        }
        return DEFAULT_NS;
    }

    private void addSchemeRelationship(Resource resource) {
        Resource ontologyResource = resourceMap.get("ontology");
        if (ontologyResource != null && resource.hasProperty(RDF.type,
                ontModel.getResource(uriGenerator.getEffectiveNamespace() + POJEM))) {
            resource.addProperty(SKOS.inScheme, ontologyResource);
        }
    }

    private Map<String, String> createModelProperties(VocabularyMetadata metadata) {
        Map<String, String> properties = new HashMap<>();
        if (metadata.getName() != null) {
            properties.put(NAZEV, metadata.getName());
        }
        if (metadata.getDescription() != null) {
            properties.put(POPIS, metadata.getDescription());
        }
        if (metadata.getNamespace() != null) {
            properties.put(LOKALNI_KATALOG, metadata.getNamespace());
        }
        return properties;
    }

    private void transformClasses(List<ClassData> classes) {
        log.debug("Transforming {} classes", classes.size());
        for (ClassData classData : classes) {
            if (!classData.hasValidData()) {
                log.warn("Skipping invalid class: {}", classData.getName());
                continue;
            }
            try {
                Resource classResource = createClassResource(classData);
                classResources.put(classData.getName(), classResource);
                resourceMap.put(classData.getName(), classResource);
                log.debug("Created class: {} -> {}", classData.getName(), classResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create class: {}", classData.getName(), e);
            }
        }
    }

    private Resource createClassResource(ClassData classData) {
        String classURI = uriGenerator.generateClassURI(classData.getName(), classData.getId());
        Resource classResource = ontModel.createResource(classURI);

        classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + POJEM));
        addSpecificClassType(classResource, classData);
        addResourceMetadata(classResource, classData.getName(), classData.getDescription(),
                classData.getDefinition(), classData.getSource(), classData.getRelatedSource(), classData.getIdentifier());
        addClassSpecificMetadata(classResource, classData);
        addSchemeRelationship(classResource);
        return classResource;
    }

    private void addSpecificClassType(Resource classResource, ClassData classData) {
        String excelType = classData.getType();

        if ("Subjekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TSP));
        } else if ("Objekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TOP));
        }
        classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TRIDA));
    }

    private void transformProperties(List<PropertyData> properties) {
        log.debug("Transforming {} properties", properties.size());
        for (PropertyData propertyData : properties) {
            try {
                Resource propertyResource = createPropertyResource(propertyData);
                propertyResources.put(propertyData.getName(), propertyResource);
                resourceMap.put(propertyData.getName(), propertyResource);
                log.debug("Created property: {} -> {}", propertyData.getName(), propertyResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create property: {}", propertyData.getName(), e);
            }
        }
    }

    private Resource createPropertyResource(PropertyData propertyData) {
        String propertyURI = uriGenerator.generatePropertyURI(propertyData.getName(), propertyData.getIdentifier());
        Resource propertyResource = ontModel.createResource(propertyURI);

        propertyResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + POJEM));
        propertyResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + VLASTNOST));

        addResourceMetadata(propertyResource, propertyData.getName(), propertyData.getDescription(),
                propertyData.getDefinition(), propertyData.getSource(), propertyData.getRelatedSource(), propertyData.getIdentifier());

        addPropertySpecificMetadata(propertyResource, propertyData);

        addDomainRelationship(propertyResource, propertyData);
        addRangeInformation(propertyResource, propertyData);
        addDataGovernanceMetadata(propertyResource, propertyData);
        addSchemeRelationship(propertyResource);
        return propertyResource;
    }

    private void addPropertySpecificMetadata(Resource propertyResource, PropertyData propertyData) {
        log.debug("Adding property-specific metadata for: {}", propertyData.getName());

        if (propertyData.getAlternativeName() != null && !propertyData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for property {}: '{}'", propertyData.getName(), propertyData.getAlternativeName());
            addAlternativeNames(propertyResource, propertyData.getAlternativeName());
        } else {
            log.debug("No alternative names found for property: {}", propertyData.getName());
        }

        if (propertyData.getEquivalentConcept() != null && !propertyData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for property {}: '{}'", propertyData.getName(), propertyData.getEquivalentConcept());
            addEquivalentConcept(propertyResource, propertyData.getEquivalentConcept(), "property");
        }
    }

    private void transformRelationships(List<RelationshipData> relationships) {
        log.debug("Transforming {} relationships", relationships.size());
        for (RelationshipData relationshipData : relationships) {
            if (!relationshipData.hasValidData()) {
                log.warn("Skipping invalid relationship: {}", relationshipData.getName());
                continue;
            }
            try {
                Resource relationshipResource = createRelationshipResource(relationshipData);
                relationshipResources.put(relationshipData.getName(), relationshipResource);
                resourceMap.put(relationshipData.getName(), relationshipResource);
                log.debug("Created relationship: {} -> {}", relationshipData.getName(), relationshipResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create relationship: {}", relationshipData.getName(), e);
            }
        }
    }

    private Resource createRelationshipResource(RelationshipData relationshipData) {
        String relationshipURI = uriGenerator.generateRelationshipURI(relationshipData.getName(),
                relationshipData.getIdentifier());
        Resource relationshipResource = ontModel.createResource(relationshipURI);

        relationshipResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + POJEM));
        relationshipResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + VZTAH));
        relationshipResource.addProperty(RDF.type, ontModel.getProperty("http://www.w3.org/2002/07/owl#ObjectProperty"));

        addResourceMetadata(relationshipResource, relationshipData.getName(), relationshipData.getDescription(),
                relationshipData.getDefinition(), relationshipData.getSource(), relationshipData.getRelatedSource(), relationshipData.getIdentifier());

        addRelationshipSpecificMetadata(relationshipResource, relationshipData);

        addDomainRangeRelationships(relationshipResource, relationshipData);
        addSchemeRelationship(relationshipResource);
        return relationshipResource;
    }

    private void addRelationshipSpecificMetadata(Resource relationshipResource, RelationshipData relationshipData) {
        log.debug("Adding relationship-specific metadata for: {}", relationshipData.getName());

        if (relationshipData.getAlternativeName() != null && !relationshipData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for relationship {}: '{}'", relationshipData.getName(), relationshipData.getAlternativeName());
            addAlternativeNames(relationshipResource, relationshipData.getAlternativeName());
        } else {
            log.debug("No alternative names found for relationship: {}", relationshipData.getName());
        }

        if (relationshipData.getEquivalentConcept() != null && !relationshipData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for relationship {}: '{}'", relationshipData.getName(), relationshipData.getEquivalentConcept());
            addEquivalentConcept(relationshipResource, relationshipData.getEquivalentConcept(), "relationship");
        }
    }

    private void transformHierarchies(List<HierarchyData> hierarchies) {
        log.debug("Transforming {} hierarchical relationships", hierarchies.size());

        if (hierarchies.isEmpty()) {
            log.debug("No hierarchies to process");
            return;
        }

        validateHierarchies(hierarchies);

        int processedHierarchies = 0;
        int skippedHierarchies = 0;

        for (HierarchyData hierarchyData : hierarchies) {
            if (!hierarchyData.hasValidData()) {
                log.warn("Skipping invalid hierarchy: {}", hierarchyData);
                skippedHierarchies++;
                continue;
            }

            try {
                boolean success = createHierarchyRelationship(hierarchyData);
                if (success) {
                    processedHierarchies++;
                    log.debug("Processed hierarchy: {} IS-A {}",
                            hierarchyData.getSubClass(), hierarchyData.getSuperClass());
                } else {
                    skippedHierarchies++;
                }
            } catch (Exception e) {
                log.error("Failed to create hierarchy relationship: {} -> {}",
                        hierarchyData.getSubClass(), hierarchyData.getSuperClass(), e);
                skippedHierarchies++;
            }
        }

        log.info("Hierarchy transformation completed: {} processed, {} skipped",
                processedHierarchies, skippedHierarchies);
    }

    private boolean createHierarchyRelationship(HierarchyData hierarchyData) {
        String subClassName = hierarchyData.getSubClass();
        String superClassName = hierarchyData.getSuperClass();

        Resource subClassResource = findClassResource(subClassName);
        Resource superClassResource = findClassResource(superClassName);

        if (subClassResource == null) {
            log.warn("Subclass resource not found: {}", subClassName);
            return false;
        }

        if (superClassResource == null) {
            log.warn("Superclass resource not found: {}", superClassName);
            return false;
        }

        subClassResource.addProperty(RDFS.subClassOf, superClassResource);

        String namespace = uriGenerator.getEffectiveNamespace();
        Property hierarchyProperty = ontModel.createProperty(namespace + "nadřazená-třída");
        subClassResource.addProperty(hierarchyProperty, superClassResource);

        log.debug("Created hierarchy relationship: {} rdfs:subClassOf {}",
                subClassResource.getURI(), superClassResource.getURI());

        addHierarchyMetadata(subClassResource, hierarchyData);

        return true;
    }

    private Resource findClassResource(String className) {
        Resource resource = classResources.get(className);
        if (resource != null) {
            return resource;
        }

        resource = propertyResources.get(className);
        if (resource != null) {
            return resource;
        }

        resource = resourceMap.get(className);
        if (resource != null) {
            log.debug("Found resource in general resource map: {}", className);
            return resource;
        }

        return null;
    }

    private void addHierarchyMetadata(Resource subClassResource, HierarchyData hierarchyData) {
        String namespace = uriGenerator.getEffectiveNamespace();

        if (hierarchyData.getDescription() != null && !hierarchyData.getDescription().trim().isEmpty()) {
            Property hierarchyDescProperty = ontModel.createProperty(namespace + "popis-hierarchie");
            DataTypeConverter.addTypedProperty(subClassResource, hierarchyDescProperty,
                    hierarchyData.getDescription(), DEFAULT_LANG, ontModel);
            log.debug("Added hierarchy description for {}: {}",
                    subClassResource.getLocalName(), hierarchyData.getDescription());
        }

        if (hierarchyData.getRelationshipName() != null &&
                !hierarchyData.getRelationshipName().trim().isEmpty() &&
                !hierarchyData.getRelationshipName().startsWith("HIER-")) {

            Property relationshipNameProperty = ontModel.createProperty(namespace + "název-vztahu");
            DataTypeConverter.addTypedProperty(subClassResource, relationshipNameProperty,
                    hierarchyData.getRelationshipName(), DEFAULT_LANG, ontModel);
            log.debug("Added relationship name for {}: {}",
                    subClassResource.getLocalName(), hierarchyData.getRelationshipName());
        }
    }

    private void validateHierarchies(List<HierarchyData> hierarchies) {
        Map<String, Set<String>> hierarchyMap = new HashMap<>();
        Set<String> circularDependencies = new HashSet<>();

        for (HierarchyData hierarchy : hierarchies) {
            if (hierarchy.hasValidData()) {
                hierarchyMap.computeIfAbsent(hierarchy.getSubClass(), k -> new HashSet<>())
                        .add(hierarchy.getSuperClass());
            }
        }

        for (String className : hierarchyMap.keySet()) {
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();

            if (hasCircularDependency(className, hierarchyMap, visited, recursionStack)) {
                circularDependencies.add(className);
            }
        }

        if (!circularDependencies.isEmpty()) {
            log.warn("Detected circular dependencies in hierarchy: {}", circularDependencies);
        }
    }

    private boolean hasCircularDependency(String className, Map<String, Set<String>> hierarchyMap,
                                          Set<String> visited, Set<String> recursionStack) {
        visited.add(className);
        recursionStack.add(className);

        Set<String> superClasses = hierarchyMap.get(className);
        if (superClasses != null) {
            for (String superClass : superClasses) {
                if (!visited.contains(superClass)) {
                    if (hasCircularDependency(superClass, hierarchyMap, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(superClass)) {
                    return true;
                }
            }
        }

        recursionStack.remove(className);
        return false;
    }

    private void addResourceMetadata(Resource resource, String name, String description,
                                     String definition, String source, String relatedSource, String identifier) {
        if (name != null && !name.trim().isEmpty()) {
            DataTypeConverter.addTypedProperty(resource, RDFS.label, name, DEFAULT_LANG, ontModel);
        }

        if (description != null && !description.trim().isEmpty()) {
            Property descProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + POPIS);
            DataTypeConverter.addTypedProperty(resource, descProperty, description, DEFAULT_LANG, ontModel);
        }

        if (definition != null && !definition.trim().isEmpty()) {
            Property defProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + DEFINICE);
            DataTypeConverter.addTypedProperty(resource, defProperty, definition, DEFAULT_LANG, ontModel);
        }

        if (identifier != null && !identifier.trim().isEmpty()) {
            Property identifierProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + IDENTIFIKATOR);

            if (DataTypeConverter.isUri(identifier)) {
                resource.addProperty(identifierProperty, ontModel.createResource(identifier));
                log.debug("Added identifier as URI: {}", identifier);
            } else {
                DataTypeConverter.addTypedProperty(resource, identifierProperty, identifier, null, ontModel);
                log.debug("Added identifier as literal: {}", identifier);
            }
        }

        if (source != null && !source.trim().isEmpty()) {
            addSourceReferences(resource, source, ZDROJ);
        }

        if (relatedSource != null && !relatedSource.trim().isEmpty()) {
            addSourceReferences(resource, relatedSource, SOUVISEJICI_ZDROJ);
        }
    }

    private void addSourceReferences(Resource resource, String sourceUrls, String constant) {
        if (sourceUrls.contains(";")) {
            addMultipleSourceUrls(resource, sourceUrls, constant);
        } else {
            addSingleSourceUrl(resource, sourceUrls, constant);
        }
    }

    private void addMultipleSourceUrls(Resource resource, String sourceUrlString, String constant) {
        String[] urls = sourceUrlString.split(";");
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                addSingleSourceUrl(resource, url.trim(), constant);
            }
        }
    }

    private void addSingleSourceUrl(Resource resource, String url, String constant) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String transformedUrl = UtilityMethods.transformEliUrl(url, removeELI);

        if (transformedUrl == null || transformedUrl.trim().isEmpty()) {
            log.debug("Skipping invalid/filtered source: {}", url);
            return;
        }

        switch (constant) {
            case ZDROJ -> {
                Property sourceProp = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + ZDROJ);

                if (DataTypeConverter.isUri(transformedUrl)) {
                    resource.addProperty(sourceProp, ontModel.createResource(transformedUrl));
                    log.debug("Added source URL as resource: {}", transformedUrl);
                } else {
                    DataTypeConverter.addTypedProperty(resource, sourceProp, transformedUrl, null, ontModel);
                    log.debug("Added source URL as typed literal: {}", transformedUrl);
                }
            }
            case SOUVISEJICI_ZDROJ -> {
                Property sourceProp = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + SOUVISEJICI_ZDROJ);

                if (DataTypeConverter.isUri(transformedUrl)) {
                    resource.addProperty(sourceProp, ontModel.createResource(transformedUrl));
                    log.debug("Added related source URL as resource: {}", transformedUrl);
                } else {
                    DataTypeConverter.addTypedProperty(resource, sourceProp, transformedUrl, null, ontModel);
                    log.debug("Added related source URL as typed literal: {}", transformedUrl);
                }
            }
            default -> {
                // continue
            }
        }
    }

    private void addClassSpecificMetadata(Resource classResource, ClassData classData) {
        addAlternativeName(classResource, classData);
        addEquivalentConcept(classResource, classData);
        addAgenda(classResource, classData);
        addAgendaInformationSystem(classResource, classData);
    }

    private void addAlternativeName(Resource classResource, ClassData classData) {
        if (classData.getAlternativeName() != null && !classData.getAlternativeName().trim().isEmpty()) {
            addAlternativeNames(classResource, classData.getAlternativeName());
        }
    }

    private void addEquivalentConcept(Resource classResource, ClassData classData) {
        if (classData.getEquivalentConcept() != null && !classData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for class {}: '{}'", classData.getName(), classData.getEquivalentConcept());
            addEquivalentConcept(classResource, classData.getEquivalentConcept(), "class");
        }
    }

    private void addEquivalentConcept(Resource resource, String equivalentConcept, String entityType) {
        if (!UtilityMethods.isValidIRI(equivalentConcept)) {
            log.warn("Skipping invalid equivalent concept for {} '{}': '{}' is not a valid IRI",
                    entityType, resource.getLocalName(), equivalentConcept);
            return;
        }

        Property exactMatchProperty = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch");
        resource.addProperty(exactMatchProperty, ontModel.createResource(equivalentConcept));
        log.debug("Added valid equivalent concept IRI for {} '{}': {}",
                entityType, resource.getLocalName(), equivalentConcept);
    }

    private void addAgenda(Resource classResource, ClassData classData) {
        if (classData.getAgendaCode() != null && !classData.getAgendaCode().trim().isEmpty()) {
            String agendaCode = classData.getAgendaCode().trim();

            if (UtilityMethods.isValidAgendaValue(agendaCode)) {
                String transformedAgenda = UtilityMethods.transformAgendaValue(agendaCode);
                Property agendaProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + AGENDA);

                if (DataTypeConverter.isUri(transformedAgenda)) {
                    classResource.addProperty(agendaProperty, ontModel.createResource(transformedAgenda));
                    log.debug("Added valid agenda as URI: {} -> {}", agendaCode, transformedAgenda);
                } else {
                    DataTypeConverter.addTypedProperty(classResource, agendaProperty, transformedAgenda, null, ontModel);
                    log.debug("Added valid agenda as literal: {} -> {}", agendaCode, transformedAgenda);
                }
            } else {
                log.warn("Invalid agenda code '{}' for class '{}' - skipping", agendaCode, classData.getName());
            }
        }
    }

    private void addAgendaInformationSystem(Resource classResource, ClassData classData) {
        if (classData.getAgendaSystemCode() != null && !classData.getAgendaSystemCode().trim().isEmpty()) {
            String aisCode = classData.getAgendaSystemCode().trim();

            if (UtilityMethods.isValidAISValue(aisCode)) {
                String transformedAIS = UtilityMethods.transformAISValue(aisCode);
                Property aisProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + AIS);

                if (DataTypeConverter.isUri(transformedAIS)) {
                    classResource.addProperty(aisProperty, ontModel.createResource(transformedAIS));
                    log.debug("Added valid AIS as URI: {} -> {}", aisCode, transformedAIS);
                } else {
                    DataTypeConverter.addTypedProperty(classResource, aisProperty, transformedAIS, null, ontModel);
                    log.debug("Added valid AIS as literal: {} -> {}", aisCode, transformedAIS);
                }
            } else {
                log.warn("Invalid AIS code '{}' for class '{}' - skipping", aisCode, classData.getName());
            }
        }
    }

    private void addAlternativeNames(Resource resource, String altNamesValue) {
        if (altNamesValue == null || altNamesValue.isEmpty()) {
            log.debug("No alternative names to add for resource: {}", resource.getLocalName());
            return;
        }

        log.debug("Processing alternative names for {}: '{}'", resource.getLocalName(),
                altNamesValue.length() > 100 ? altNamesValue.substring(0, 100) + "..." : altNamesValue);

        Property altNameProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + ALTERNATIVNI_NAZEV);

        if (!altNamesValue.contains(";")) {
            DataTypeConverter.addTypedProperty(resource, altNameProperty, altNamesValue, DEFAULT_LANG, ontModel);
            log.debug("Added single alternative name for {}: {}", resource.getLocalName(),
                    altNamesValue.length() > 50 ? altNamesValue.substring(0, 50) + "..." : altNamesValue);
            return;
        }

        String[] altNames = altNamesValue.split(";");
        log.debug("Found {} alternative names separated by semicolons for {}", altNames.length, resource.getLocalName());

        for (String name : altNames) {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                DataTypeConverter.addTypedProperty(resource, altNameProperty, trimmedName, DEFAULT_LANG, ontModel);
                log.debug("Added alternative name for {}: {}", resource.getLocalName(),
                        trimmedName.length() > 50 ? trimmedName.substring(0, 50) + "..." : trimmedName);
            }
        }

        log.debug("Finished processing alternative names for {}", resource.getLocalName());
    }

    private void addDomainRelationship(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getDomain() != null && !propertyData.getDomain().trim().isEmpty()) {
            Property domainProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + DEFINICNI_OBOR);
            addResourceReference(propertyResource, domainProperty, propertyData.getDomain(), classResources);
        }
    }

    private void addRangeInformation(Resource propertyResource, PropertyData propertyData) {
        String dataType = propertyData.getDataType();

        if (dataType != null && !dataType.trim().isEmpty()) {
            Property rangeProperty = RDFS.range;

            if (dataType.startsWith("xsd:")) {
                String xsdType = XSD + dataType.substring(4);
                if (DataTypeConverter.isValidXSDType(dataType.substring(4))) {
                    propertyResource.addProperty(rangeProperty, ontModel.createResource(xsdType));
                    log.debug("Added valid XSD range type: {}", xsdType);
                } else {
                    propertyResource.addProperty(rangeProperty, ontModel.createLiteral(dataType));
                    log.debug("Added invalid XSD type '{}' as plain string literal", dataType);
                }
            } else if (DataTypeConverter.isUri(dataType)) {
                propertyResource.addProperty(rangeProperty, ontModel.createResource(dataType));
                log.debug("Added URI range type: {}", dataType);
            } else {
                Resource existingClass = classResources.get(dataType);
                if (existingClass != null) {
                    propertyResource.addProperty(rangeProperty, existingClass);
                    log.debug("Added local class range: {}", dataType);
                } else {
                    propertyResource.addProperty(rangeProperty, ontModel.createLiteral(dataType));
                    log.debug("Added unknown data type '{}' as plain string literal", dataType);
                }
            }
        }
    }

    private void addDataGovernanceMetadata(Resource propertyResource, PropertyData propertyData) {
        addPPDFData(propertyResource, propertyData);
        addPublicOrNonPublicData(propertyResource, propertyData);
        handleGovernanceProperty(propertyResource, propertyData.getSharingMethod(), "sharing-method");
        handleGovernanceProperty(propertyResource, propertyData.getAcquisitionMethod(), "acquisition-method");
        handleGovernanceProperty(propertyResource, propertyData.getContentType(), "content-type");
    }

    private void addPPDFData(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getSharedInPPDF() != null && !propertyData.getSharedInPPDF().trim().isEmpty()) {
            String value = propertyData.getSharedInPPDF();
            Property ppdfProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + JE_PPDF);

            if (UtilityMethods.isBooleanValue(value)) {
                Boolean boolValue = UtilityMethods.normalizeCzechBoolean(value);
                DataTypeConverter.addTypedProperty(propertyResource, ppdfProperty,
                        boolValue.toString(), null, ontModel);
                log.debug("Added normalized PPDF boolean: {} -> {}", value, boolValue);
            } else {
                log.warn("Unrecognized boolean value for PPDF property: '{}'", value);
                DataTypeConverter.addTypedProperty(propertyResource, ppdfProperty, value, null, ontModel);
            }
        }
    }

    private void addPublicOrNonPublicData(Resource propertyResource, PropertyData propertyData) {
        String isPublicValue = propertyData.getIsPublic();
        String privacyProvision = propertyData.getPrivacyProvision();

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            handleNonPublicData(propertyResource, propertyData, privacyProvision);
            return;
        }

        if (isPublicValue != null && !isPublicValue.trim().isEmpty()) {
            if (UtilityMethods.isBooleanValue(isPublicValue)) {
                Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

                if (Boolean.TRUE.equals(isPublic)) {
                    if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                        log.warn("Concept '{}' marked as public but has privacy provision '{}' - treating as non-public",
                                propertyData.getName(), privacyProvision);
                        handleNonPublicData(propertyResource, propertyData, privacyProvision);
                    } else {
                        propertyResource.addProperty(RDF.type,
                                ontModel.getResource(uriGenerator.getEffectiveNamespace() + VEREJNY_UDAJ));
                        log.debug("Added public data type for concept: {}", propertyData.getName());
                    }
                } else {
                    if (privacyProvision == null || privacyProvision.trim().isEmpty()) {
                        log.warn("Concept '{}' marked as non-public but has no privacy provision - adding non-public type anyway",
                                propertyData.getName());
                    }
                    handleNonPublicData(propertyResource, propertyData, privacyProvision);
                }
            } else {
                log.warn("Unrecognized boolean value for public property: '{}' for concept '{}'",
                        isPublicValue, propertyData.getName());
            }
        }
    }

    private void handleNonPublicData(Resource propertyResource, PropertyData propertyData, String privacyProvision) {
        propertyResource.addProperty(RDF.type,
                ontModel.getResource(uriGenerator.getEffectiveNamespace() + NEVEREJNY_UDAJ));

        log.debug("Added non-public data type for concept: {}", propertyData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddPrivacyProvision(propertyResource, propertyData, privacyProvision);
        }
    }

    private void validateAndAddPrivacyProvision(Resource propertyResource, PropertyData propertyData, String provision) {
        String trimmedProvision = provision.trim();

        String transformedProvision = UtilityMethods.transformEliPrivacyProvision(trimmedProvision, removeELI);

        if (transformedProvision != null && !transformedProvision.trim().isEmpty()) {
            Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + USTANOVENI_NEVEREJNOST);

            if (DataTypeConverter.isUri(transformedProvision)) {
                propertyResource.addProperty(provisionProperty, ontModel.createResource(transformedProvision));
                log.debug("Added privacy provision as URI for concept '{}': {} -> {}",
                        propertyData.getName(), trimmedProvision, transformedProvision);
            } else {
                DataTypeConverter.addTypedProperty(propertyResource, provisionProperty, transformedProvision, null, ontModel);
                log.debug("Added privacy provision as literal for concept '{}': {} -> {}",
                        propertyData.getName(), trimmedProvision, transformedProvision);
            }
        } else {
            log.warn("Privacy provision transformation resulted in empty value for concept '{}': '{}'",
                    propertyData.getName(), trimmedProvision);
        }
    }

    private void handleGovernanceProperty(Resource resource, String value, String propertyType) {
        if (value == null || value.trim().isEmpty()) {
            log.debug("Skipping empty governance property '{}' for resource: {}", propertyType, resource.getLocalName());
            return;
        }

        String propertyConstant = getGovernancePropertyConstant(propertyType);

        log.debug("Using constant '{}' for governance property type '{}'", propertyConstant, propertyType);
        addGovernanceProperty(resource, value, propertyConstant);
    }

    private String getGovernancePropertyConstant(String propertyType) {
        return switch (propertyType) {
            case "sharing-method" -> getConstantValue(ZPUSOB_SDILENI_UDEJE, ZPUSOB_SDILENI, "způsob-sdílení-údaje");
            case "acquisition-method" -> getConstantValue(ZPUSOB_ZISKANI_UDEJE, ZPUSOB_ZISKANI, "způsob-získání-údaje");
            case "content-type" -> getConstantValue(TYP_OBSAHU_UDAJE, TYP_OBSAHU, "typ-obsahu-údaje");
            default -> {
                log.warn("Unknown governance property type: {}", propertyType);
                yield null;
            }
        };
    }

    private String getConstantValue(String optionOne, String optionTwo, String defaultValue) {
        if (optionOne != null && !optionOne.trim().isEmpty()) {
            return UtilityMethods.sanitizeForIRI(optionOne);
        } else if (optionTwo != null && !optionTwo.trim().isEmpty()) {
            return optionTwo;
        } else {
            return defaultValue;
        }
    }

    private void addGovernanceProperty(Resource resource, String value, String propertyName) {
        if (value != null && !value.trim().isEmpty()) {
            Property property = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);

            if (DataTypeConverter.isUri(value)) {
                resource.addProperty(property, ontModel.createResource(value));
                log.debug("Added governance property {} as URI: {}", propertyName, value);
            } else {
                DataTypeConverter.addTypedProperty(resource, property, value, null, ontModel);
                log.debug("Added governance property {} as typed literal: {}", propertyName, value);
            }
        }
    }

    private void addDomainRangeRelationships(Resource relationshipResource, RelationshipData relationshipData) {
        if (relationshipData.getDomain() != null && !relationshipData.getDomain().trim().isEmpty()) {
            Property domainProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + DEFINICNI_OBOR);
            addResourceReference(relationshipResource, domainProperty, relationshipData.getDomain(), classResources);
        }

        if (relationshipData.getRange() != null && !relationshipData.getRange().trim().isEmpty()) {
            Property rangeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + OBOR_HODNOT);
            addResourceReference(relationshipResource, rangeProperty, relationshipData.getRange(), classResources);
        }
    }

    private void addResourceReference(Resource subject, Property property, String referenceName,
                                      Map<String, Resource> resourceMap) {
        Resource existingResource = resourceMap.get(referenceName);
        if (existingResource != null) {
            subject.addProperty(property, existingResource);
            log.debug("Added local resource reference: {} -> {}", property.getLocalName(), referenceName);
            return;
        }

        if (DataTypeConverter.isUri(referenceName)) {
            subject.addProperty(property, ontModel.createResource(referenceName));
            log.debug("Added URI resource reference: {} -> {}", property.getLocalName(), referenceName);
        } else {
            DataTypeConverter.addTypedProperty(subject, property, referenceName, null, ontModel);
            log.debug("Added typed literal reference: {} -> {}", property.getLocalName(), referenceName);
        }
    }
}