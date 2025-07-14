package com.dia.converter.transformer;

import com.dia.converter.data.*;
import com.dia.converter.transformer.metadata.ResourceMetadata;
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
import static com.dia.constants.ArchiConstants.JE_PPDF;
import static com.dia.constants.ExcelConstants.*;
import static com.dia.constants.ExportConstants.Common.*;
import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;
import static com.dia.constants.ExportConstants.Json.NAZEV;
import static com.dia.constants.ExportConstants.Json.POPIS;


@Component
@Slf4j
@Getter
public class OFNDataTransformer {

    private OntModel ontModel;
    private final URIGenerator uriGenerator;

    public OFNDataTransformer() {
        this.uriGenerator = new URIGenerator();
    }

    public TransformationResult transform(OntologyData ontologyData, Boolean removeELI) throws ConversionException {
        try {
            log.info("Starting ontology data transformation...");

            if (ontologyData == null || ontologyData.getVocabularyMetadata() == null) {
                throw new ConversionException("Invalid ontology data");
            }

            Set<String> requiredBaseClasses = analyzeRequiredBaseClasses(ontologyData);
            Set<String> requiredProperties = analyzeRequiredProperties(ontologyData);

            log.debug("Required base classes: {}", requiredBaseClasses);
            log.debug("Required properties: {}", requiredProperties);

            OFNBaseModel dynamicBaseModel = new OFNBaseModel(requiredBaseClasses, requiredProperties);
            this.ontModel = dynamicBaseModel.getOntModel();

            String effectiveNamespace = determineEffectiveNamespace(ontologyData.getVocabularyMetadata());
            uriGenerator.setEffectiveNamespace(effectiveNamespace);
            uriGenerator.setVocabularyName(ontologyData.getVocabularyMetadata().getName());

            log.debug("Using effective namespace: {}", effectiveNamespace);
            log.debug("Model name: {}", ontologyData.getVocabularyMetadata().getName());

            Map<String, Resource> localResourceMap = new HashMap<>();
            Map<String, Resource> localClassResources = new HashMap<>();
            Map<String, Resource> localPropertyResources = new HashMap<>();
            Map<String, Resource> localRelationshipResources = new HashMap<>();

            createOntologyResource(ontologyData.getVocabularyMetadata(), localResourceMap);

            transformClasses(ontologyData.getClasses(), localClassResources, localResourceMap, removeELI);
            transformProperties(ontologyData.getProperties(), localPropertyResources, localResourceMap, removeELI);
            transformRelationships(ontologyData.getRelationships(), localRelationshipResources, localResourceMap, removeELI);
            transformHierarchies(ontologyData.getHierarchies(), localClassResources, localPropertyResources, localResourceMap);

            Map<String, String> modelProperties = createModelProperties(ontologyData.getVocabularyMetadata());

            log.info("Transformation completed successfully. Created {} classes, {} properties, {} relationships",
                    localClassResources.size(), localPropertyResources.size(), localRelationshipResources.size());

            return new TransformationResult(
                    ontModel,
                    new HashMap<>(localResourceMap),
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
        log.info("Starting JSON export: requestId={}", requestId);

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
        log.info("Starting Turtle export: requestId={}", requestId);

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

    private Set<String> analyzeRequiredBaseClasses(OntologyData ontologyData) {
        Set<String> requiredClasses = new HashSet<>();

        if (hasAnyContent(ontologyData)) {
            requiredClasses.add(POJEM);
        }

        addClassSpecificRequirements(ontologyData, requiredClasses);
        addPropertySpecificRequirements(ontologyData, requiredClasses);
        addRelationshipSpecificRequirements(ontologyData, requiredClasses);

        log.debug("Analysis found {} required base classes", requiredClasses.size());
        return requiredClasses;
    }

    private boolean hasAnyContent(OntologyData ontologyData) {
        return !ontologyData.getClasses().isEmpty() ||
                !ontologyData.getProperties().isEmpty() ||
                !ontologyData.getRelationships().isEmpty();
    }

    private void addClassSpecificRequirements(OntologyData ontologyData, Set<String> requiredClasses) {
        if (ontologyData.getClasses().isEmpty()) {
            return;
        }

        requiredClasses.add(TRIDA);

        for (ClassData classData : ontologyData.getClasses()) {
            String type = classData.getType();
            if (type != null) {
                if (isSubjectType(type)) {
                    requiredClasses.add(TSP);
                } else if (isObjectType(type)) {
                    requiredClasses.add(TOP);
                }
            }
        }
    }

    private void addPropertySpecificRequirements(OntologyData ontologyData, Set<String> requiredClasses) {
        if (ontologyData.getProperties().isEmpty()) {
            return;
        }

        requiredClasses.add(VLASTNOST);

        for (PropertyData prop : ontologyData.getProperties()) {
            if (hasPublicDataClassification(prop)) {
                requiredClasses.add(VEREJNY_UDAJ);
            }
            if (hasPrivateDataClassification(prop)) {
                requiredClasses.add(NEVEREJNY_UDAJ);
            }
        }
    }

    private void addRelationshipSpecificRequirements(OntologyData ontologyData, Set<String> requiredClasses) {
        if (!ontologyData.getRelationships().isEmpty()) {
            requiredClasses.add(VZTAH);
        }
    }

    private boolean isSubjectType(String type) {
        return type.contains("subjekt") ||
                type.equalsIgnoreCase("typ subjektu") ||
                type.equalsIgnoreCase("Subjekt práva");
    }

    private boolean isObjectType(String type) {
        return type.contains("objekt") ||
                type.equalsIgnoreCase("typ objektu") ||
                type.equalsIgnoreCase("Objekt práva");
    }

    private Set<String> analyzeRequiredProperties(OntologyData ontologyData) {
        RequiredPropertiesAnalyzer analyzer = new RequiredPropertiesAnalyzer(ontologyData);
        return analyzer.analyze();
    }

    private boolean hasPublicDataClassification(PropertyData prop) {
        return prop.getIsPublic() != null &&
                (prop.getIsPublic().toLowerCase().contains("ano") ||
                        prop.getIsPublic().toLowerCase().contains("true") ||
                        prop.getIsPublic().equalsIgnoreCase("yes"));
    }

    private boolean hasPrivateDataClassification(PropertyData prop) {
        return (prop.getIsPublic() != null &&
                (prop.getIsPublic().toLowerCase().contains("ne") ||
                        prop.getIsPublic().toLowerCase().contains("false") ||
                        prop.getIsPublic().equalsIgnoreCase("no"))) ||
                (prop.getPrivacyProvision() != null && !prop.getPrivacyProvision().trim().isEmpty());
    }

    private void createOntologyResource(VocabularyMetadata metadata, Map<String, Resource> localResourceMap) {
        String ontologyIRI = uriGenerator.generateVocabularyURI(metadata.getName(), null);
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

            localResourceMap.put("ontology", ontologyResource);
            log.debug("Ontology resource created successfully: {}", ontologyIRI);
        }
    }

    private String determineEffectiveNamespace(VocabularyMetadata metadata) {
        String namespace = metadata.getNamespace();
        if (namespace != null && !namespace.trim().isEmpty() && UtilityMethods.isValidUrl(namespace)) {
            return UtilityMethods.ensureNamespaceEndsWithDelimiter(namespace);
        }
        return DEFAULT_NS;
    }

    private void addSchemeRelationship(Resource resource, Map<String, Resource> localResourceMap) {
        Resource ontologyResource = localResourceMap.get("ontology");
        if (ontologyResource != null && resource.hasProperty(RDF.type,
                ontModel.getResource(OFN_NAMESPACE + POJEM))) {
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

    private void transformClasses(List<ClassData> classes, Map<String, Resource> localClassResources,
                                  Map<String, Resource> localResourceMap, Boolean removeELI) {
        log.debug("Transforming {} classes", classes.size());
        for (ClassData classData : classes) {
            if (!classData.hasValidData()) {
                log.warn("Skipping invalid class: {}", classData.getName());
                continue;
            }
            try {
                Resource classResource = createClassResource(classData, localResourceMap, removeELI);
                localClassResources.put(classData.getName(), classResource);
                localResourceMap.put(classData.getName(), classResource);
                log.debug("Created class: {} -> {}", classData.getName(), classResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create class: {}", classData.getName(), e);
            }
        }
    }

    private Resource createClassResource(ClassData classData, Map<String, Resource> localResourceMap, Boolean removeELI) {
        String classURI = uriGenerator.generateConceptURI(classData.getName(), classData.getIdentifier());
        Resource classResource = ontModel.createResource(classURI);

        classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        addSpecificClassType(classResource, classData);

        addResourceMetadata(classResource, ResourceMetadata.from(classData), removeELI);

        addClassSpecificMetadata(classResource, classData);
        addSchemeRelationship(classResource, localResourceMap);
        return classResource;
    }

    private void addSpecificClassType(Resource classResource, ClassData classData) {
        String excelType = classData.getType();

        if ("Subjekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + TSP));
        } else if ("Objekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + TOP));
        }
        classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + TRIDA));
    }

    private void transformProperties(List<PropertyData> properties, Map<String, Resource> localPropertyResources,
                                     Map<String, Resource> localResourceMap, Boolean removeELI) {
        log.debug("Transforming {} properties", properties.size());
        for (PropertyData propertyData : properties) {
            try {
                Resource propertyResource = createPropertyResource(propertyData, localResourceMap, removeELI);
                localPropertyResources.put(propertyData.getName(), propertyResource);
                localResourceMap.put(propertyData.getName(), propertyResource);
                log.debug("Created property: {} -> {}", propertyData.getName(), propertyResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create property: {}", propertyData.getName(), e);
            }
        }
    }

    private Resource createPropertyResource(PropertyData propertyData, Map<String, Resource> localResourceMap, Boolean removeELI) {
        String propertyURI = uriGenerator.generateConceptURI(propertyData.getName(), propertyData.getIdentifier());
        Resource propertyResource = ontModel.createResource(propertyURI);

        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VLASTNOST));

        addResourceMetadata(propertyResource, ResourceMetadata.from(propertyData), removeELI);

        addPropertySpecificMetadata(propertyResource, propertyData);
        addDataGovernanceMetadata(propertyResource, propertyData, removeELI);
        addSchemeRelationship(propertyResource, localResourceMap);
        return propertyResource;
    }

    private void addPropertySpecificMetadata(Resource propertyResource, PropertyData propertyData) {
        log.debug("Adding property-specific metadata for: {}", propertyData.getName());

        if (propertyData.getAlternativeName() != null && !propertyData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for property {}: '{}'", propertyData.getName(), propertyData.getAlternativeName());
            addAlternativeNames(propertyResource, propertyData.getAlternativeName());
        }

        if (propertyData.getEquivalentConcept() != null && !propertyData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for property {}: '{}'", propertyData.getName(), propertyData.getEquivalentConcept());
            addEquivalentConcept(propertyResource, propertyData.getEquivalentConcept(), "property");
        }

        if (propertyData.getDomain() != null && !propertyData.getDomain().trim().isEmpty()) {
            addResourceReference(propertyResource, RDFS.domain, propertyData.getDomain());
        }

        addRangeInformation(propertyResource, propertyData);
    }

    private void transformRelationships(List<RelationshipData> relationships, Map<String, Resource> localRelationshipResources,
                                        Map<String, Resource> localResourceMap, Boolean removeELI) {
        log.debug("Transforming {} relationships", relationships.size());
        for (RelationshipData relationshipData : relationships) {
            if (!relationshipData.hasValidData()) {
                log.warn("Skipping invalid relationship: {}", relationshipData.getName());
                continue;
            }
            try {
                Resource relationshipResource = createRelationshipResource(relationshipData, localResourceMap, removeELI);
                localRelationshipResources.put(relationshipData.getName(), relationshipResource);
                localResourceMap.put(relationshipData.getName(), relationshipResource);
                log.debug("Created relationship: {} -> {}", relationshipData.getName(), relationshipResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create relationship: {}", relationshipData.getName(), e);
            }
        }
    }

    private Resource createRelationshipResource(RelationshipData relationshipData, Map<String, Resource> localResourceMap, Boolean removeELI) {
        String relationshipURI = uriGenerator.generateConceptURI(relationshipData.getName(),
                relationshipData.getIdentifier());
        Resource relationshipResource = ontModel.createResource(relationshipURI);

        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VZTAH));
        relationshipResource.addProperty(RDF.type, ontModel.getProperty("http://www.w3.org/2002/07/owl#ObjectProperty"));

        addResourceMetadata(relationshipResource, ResourceMetadata.from(relationshipData), removeELI);

        addRelationshipSpecificMetadata(relationshipResource, relationshipData);
        addDomainRangeRelationships(relationshipResource, relationshipData);
        addSchemeRelationship(relationshipResource, localResourceMap);
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

    private void transformHierarchies(List<HierarchyData> hierarchies, Map<String, Resource> localClassResources,
                                      Map<String, Resource> localPropertyResources, Map<String, Resource> localResourceMap) {
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
                boolean success = createHierarchyRelationship(hierarchyData, localClassResources, localPropertyResources, localResourceMap);
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

    private boolean createHierarchyRelationship(HierarchyData hierarchyData, Map<String, Resource> localClassResources,
                                                Map<String, Resource> localPropertyResources, Map<String, Resource> localResourceMap) {
        String subClassName = hierarchyData.getSubClass();
        String superClassName = hierarchyData.getSuperClass();

        Resource subClassResource = findClassResource(subClassName, localClassResources, localPropertyResources, localResourceMap);
        Resource superClassResource = findClassResource(superClassName, localClassResources, localPropertyResources, localResourceMap);

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

    private Resource findClassResource(String className, Map<String, Resource> localClassResources,
                                       Map<String, Resource> localPropertyResources, Map<String, Resource> localResourceMap) {
        Resource resource = localClassResources.get(className);
        if (resource != null) {
            return resource;
        }

        resource = localPropertyResources.get(className);
        if (resource != null) {
            return resource;
        }

        resource = localResourceMap.get(className);
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

    private void addResourceMetadata(Resource resource, ResourceMetadata metadata, Boolean removeELI) {
        if (metadata.name() != null && !metadata.name().trim().isEmpty()) {
            DataTypeConverter.addTypedProperty(resource, SKOS.prefLabel, metadata.name(), DEFAULT_LANG, ontModel);
        }

        if (metadata.description() != null && !metadata.description().trim().isEmpty()) {
            Property descProperty = ontModel.createProperty("http://purl.org/dc/terms/description");
            DataTypeConverter.addTypedProperty(resource, descProperty, metadata.description(), DEFAULT_LANG, ontModel);
        }

        if (metadata.definition() != null && !metadata.definition().trim().isEmpty()) {
            DataTypeConverter.addTypedProperty(resource, SKOS.definition, metadata.definition(), DEFAULT_LANG, ontModel);
        }

        if (metadata.identifier() != null && !metadata.identifier().trim().isEmpty()) {
            Property identifierProperty = ontModel.createProperty("http://purl.org/dc/terms/identifier");

            if (DataTypeConverter.isUri(metadata.identifier())) {
                resource.addProperty(identifierProperty, ontModel.createResource(metadata.identifier()));
                log.debug("Added identifier as URI: {}", metadata.identifier());
            } else {
                DataTypeConverter.addTypedProperty(resource, identifierProperty, metadata.identifier(), null, ontModel);
                log.debug("Added identifier as literal: {}", metadata.identifier());
            }
        }

        if (metadata.source() != null && !metadata.source().trim().isEmpty()) {
            addSourceReferences(resource, metadata.source(), "dcterms:source", removeELI);
        }

        if (metadata.relatedSource() != null && !metadata.relatedSource().trim().isEmpty()) {
            addSourceReferences(resource, metadata.relatedSource(), "dcterms:references", removeELI);
        }
    }

    private void addSourceReferences(Resource resource, String sourceUrls, String constant, Boolean removeELI) {
        if (sourceUrls.contains(";")) {
            addMultipleSourceUrls(resource, sourceUrls, constant, removeELI);
        } else {
            addSingleSourceUrl(resource, sourceUrls, constant, removeELI);
        }
    }

    private void addMultipleSourceUrls(Resource resource, String sourceUrlString, String constant, Boolean removeELI) {
        String[] urls = sourceUrlString.split(";");
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                addSingleSourceUrl(resource, url.trim(), constant, removeELI);
            }
        }
    }

    private void addSingleSourceUrl(Resource resource, String url, String constant, Boolean removeELI) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String transformedUrl = UtilityMethods.transformEliUrl(url, removeELI);

        if (transformedUrl == null || transformedUrl.trim().isEmpty()) {
            log.debug("Skipping invalid/filtered source: {}", url);
            return;
        }

        Property sourceProp = switch (constant) {
            case "dcterms:source" -> ontModel.createProperty("http://purl.org/dc/terms/source");
            case "dcterms:references" -> ontModel.createProperty("http://purl.org/dc/terms/references");
            default -> ontModel.createProperty(uriGenerator.getEffectiveNamespace() + constant);
        };

        if (DataTypeConverter.isUri(transformedUrl)) {
            resource.addProperty(sourceProp, ontModel.createResource(transformedUrl));
            log.debug("Added source URL as resource: {}", transformedUrl);
        } else {
            DataTypeConverter.addTypedProperty(resource, sourceProp, transformedUrl, null, ontModel);
            log.debug("Added source URL as typed literal: {}", transformedUrl);
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
                propertyResource.addProperty(rangeProperty, ontModel.createLiteral(dataType));
                log.debug("Added unknown data type '{}' as plain string literal", dataType);
            }
        }
    }

    private void addDataGovernanceMetadata(Resource propertyResource, PropertyData propertyData, Boolean removeELI) {
        addPPDFData(propertyResource, propertyData);
        addPublicOrNonPublicData(propertyResource, propertyData, removeELI);
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

    private void addPublicOrNonPublicData(Resource propertyResource, PropertyData propertyData, Boolean removeELI) {
        String isPublicValue = propertyData.getIsPublic();
        String privacyProvision = propertyData.getPrivacyProvision();

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            handleNonPublicData(propertyResource, propertyData, privacyProvision, removeELI);
            return;
        }

        if (isPublicValue != null && !isPublicValue.trim().isEmpty()) {
            if (UtilityMethods.isBooleanValue(isPublicValue)) {
                Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

                if (Boolean.TRUE.equals(isPublic)) {
                    if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                        log.warn("Concept '{}' marked as public but has privacy provision '{}' - treating as non-public",
                                propertyData.getName(), privacyProvision);
                        handleNonPublicData(propertyResource, propertyData, privacyProvision, removeELI);
                    } else {
                        propertyResource.addProperty(RDF.type,
                                ontModel.getResource(OFN_NAMESPACE + VEREJNY_UDAJ));
                        log.debug("Added public data type for concept: {}", propertyData.getName());
                    }
                } else {
                    if (privacyProvision == null || privacyProvision.trim().isEmpty()) {
                        log.warn("Concept '{}' marked as non-public but has no privacy provision - adding non-public type anyway",
                                propertyData.getName());
                    }
                    handleNonPublicData(propertyResource, propertyData, privacyProvision, removeELI);
                }
            } else {
                log.warn("Unrecognized boolean value for public property: '{}' for concept '{}'",
                        isPublicValue, propertyData.getName());
            }
        }
    }

    private void handleNonPublicData(Resource propertyResource, PropertyData propertyData, String privacyProvision, Boolean removeELI) {
        propertyResource.addProperty(RDF.type,
                ontModel.getResource(OFN_NAMESPACE + NEVEREJNY_UDAJ));

        log.debug("Added non-public data type for concept: {}", propertyData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddPrivacyProvision(propertyResource, propertyData, privacyProvision, removeELI);
        }
    }

    private void validateAndAddPrivacyProvision(Resource propertyResource, PropertyData propertyData, String provision, Boolean removeELI) {
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
            addResourceReference(relationshipResource, RDFS.domain, relationshipData.getDomain());
        }

        if (relationshipData.getRange() != null && !relationshipData.getRange().trim().isEmpty()) {
            addResourceReference(relationshipResource, RDFS.range, relationshipData.getRange());
        }
    }

    private void addResourceReference(Resource subject, Property property, String referenceName) {
        if (DataTypeConverter.isUri(referenceName)) {
            subject.addProperty(property, ontModel.createResource(referenceName));
            log.debug("Added URI resource reference: {} -> {}", property.getLocalName(), referenceName);
        } else {
            if (property.equals(RDFS.domain) || property.equals(RDFS.range)) {
                String conceptUri = uriGenerator.generateConceptURI(referenceName, null);
                subject.addProperty(property, ontModel.createResource(conceptUri));
                log.debug("Added generated URI reference for domain/range: {} -> {}", property.getLocalName(), conceptUri);
            } else {
                DataTypeConverter.addTypedProperty(subject, property, referenceName, null, ontModel);
                log.debug("Added typed literal reference: {} -> {}", property.getLocalName(), referenceName);
            }
        }
    }
}