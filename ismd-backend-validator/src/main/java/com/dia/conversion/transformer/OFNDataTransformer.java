package com.dia.conversion.transformer;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.metadata.ResourceMetadata;
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
import static com.dia.constants.ArchiConstants.SLOVNIK;
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

    public TransformationResult transform(OntologyData ontologyData) throws ConversionException {
        try {
            log.info("Starting ontology data transformation with temporal support...");

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

            createOntologyResourceWithTemporal(ontologyData.getVocabularyMetadata(), localResourceMap);

            transformClasses(ontologyData.getClasses(), localClassResources, localResourceMap);
            transformProperties(ontologyData.getProperties(), localPropertyResources, localResourceMap);
            transformRelationships(ontologyData.getRelationships(), localRelationshipResources, localResourceMap);
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
    
    private boolean belongsToCurrentVocabulary(String conceptURI) {
        if (conceptURI == null || uriGenerator.getEffectiveNamespace() == null) {
            return false;
        }

        boolean belongs = conceptURI.startsWith(uriGenerator.getEffectiveNamespace());
        log.debug("Namespace check for {}: belongs to current vocabulary = {} (effective namespace: {})",
                conceptURI, belongs, uriGenerator.getEffectiveNamespace());
        return belongs;
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

    private void addRelationshipSpecificRequirements(OntologyData ontologyData, Set<String> requiredClasses) {
        if (!ontologyData.getRelationships().isEmpty()) {
            requiredClasses.add(VZTAH);
        }
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

    private void createOntologyResourceWithTemporal(VocabularyMetadata metadata, Map<String, Resource> localResourceMap) {
        String ontologyIRI = uriGenerator.generateVocabularyURI(metadata.getName(), null);
        log.debug("Creating ontology resource with temporal support and IRI: {}", ontologyIRI);

        ontModel.createOntology(ontologyIRI);
        Resource ontologyResource = ontModel.getResource(ontologyIRI);

        if (ontologyResource != null) {
            ontologyResource.addProperty(RDF.type, ontModel.getResource("http://www.w3.org/2002/07/owl#Ontology"));
            ontologyResource.addProperty(RDF.type, SKOS.ConceptScheme);

            ontologyResource.addProperty(RDF.type, ontModel.getResource(SLOVNIKY_NS + SLOVNIK));

            if (metadata.getName() != null && !metadata.getName().trim().isEmpty()) {
                ontologyResource.addProperty(SKOS.prefLabel, metadata.getName(), DEFAULT_LANG);
            }

            if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
                Property descProperty = ontModel.createProperty("http://purl.org/dc/terms/description");
                DataTypeConverter.addTypedProperty(ontologyResource, descProperty, metadata.getDescription(), DEFAULT_LANG, ontModel);
            }

            addTemporalMetadata(ontologyResource, metadata);

            localResourceMap.put("ontology", ontologyResource);
            log.debug("Ontology resource with temporal support created successfully: {}", ontologyIRI);
        }
    }

    private void addTemporalMetadata(Resource vocabularyResource, VocabularyMetadata metadata) {
        if (metadata.getDateOfCreation() != null && !metadata.getDateOfCreation().trim().isEmpty()) {
            Resource creationInstant = createTemporalInstant("vytvoření", metadata.getDateOfCreation());
            if (creationInstant != null) {
                Property creationProperty = ontModel.createProperty(SLOVNIKY_NS + OKAMZIK_VYTVORENI);
                vocabularyResource.addProperty(creationProperty, creationInstant);
                log.debug("Added creation temporal instant to vocabulary with date: {}", metadata.getDateOfCreation());
            }
        }

        if (metadata.getDateOfModification() != null && !metadata.getDateOfModification().trim().isEmpty()) {
            Resource modificationInstant = createTemporalInstant("poslední-změna", metadata.getDateOfModification());
            if (modificationInstant != null) {
                Property modificationProperty = ontModel.createProperty(SLOVNIKY_NS + OKAMZIK_POSLEDNI_ZMENY);
                vocabularyResource.addProperty(modificationProperty, modificationInstant);
                log.debug("Added modification temporal instant to vocabulary with date: {}", metadata.getDateOfModification());
            }
        }
    }

    private Resource createTemporalInstant(String purpose, String dateTimeValue) {
        try {
            if (dateTimeValue == null || dateTimeValue.trim().isEmpty()) {
                log.warn("Cannot create temporal instant for purpose '{}': empty date/time value", purpose);
                return null;
            }

            String trimmedValue = dateTimeValue.trim();
            String instantURI = uriGenerator.getEffectiveNamespace() + "časový-okamžik-" + purpose + "-" + System.currentTimeMillis();
            Resource instantResource = ontModel.createResource(instantURI);

            instantResource.addProperty(RDF.type, ontModel.getResource(CAS_NS + CASOVY_OKAMZIK));

            if (trimmedValue.contains("T")) {
                Property dateTimeProperty = ontModel.createProperty(CAS_NS + DATUM_A_CAS);
                DataTypeConverter.addTypedProperty(instantResource, dateTimeProperty, trimmedValue, null, ontModel);
                log.debug("Added dateTime property to temporal instant for purpose '{}': {}", purpose, trimmedValue);
            } else {
                Property dateProperty = ontModel.createProperty(CAS_NS + DATUM);
                DataTypeConverter.addTypedProperty(instantResource, dateProperty, trimmedValue, null, ontModel);
                log.debug("Added date property to temporal instant for purpose '{}': {}", purpose, trimmedValue);
            }

            return instantResource;
        } catch (Exception e) {
            log.error("Failed to create temporal instant for purpose '{}' with value '{}': {}",
                    purpose, dateTimeValue, e.getMessage(), e);
            return null;
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
                                  Map<String, Resource> localResourceMap) {
        log.debug("Transforming {} classes", classes.size());
        for (ClassData classData : classes) {
            if (!classData.hasValidData()) {
                log.warn("Skipping invalid class: {}", classData.getName());
                continue;
            }
            try {
                Resource classResource = createClassResource(classData, localResourceMap);
                localClassResources.put(classData.getName(), classResource);
                localResourceMap.put(classData.getName(), classResource);
                log.debug("Created class: {} -> {}", classData.getName(), classResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create class: {}", classData.getName(), e);
            }
        }
    }

    private Resource createClassResource(ClassData classData, Map<String, Resource> localResourceMap) {
        String classURI = uriGenerator.generateConceptURI(classData.getName(), classData.getIdentifier());
        Resource classResource = ontModel.createResource(classURI);

        classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        addSpecificClassType(classResource, classData);

        if (belongsToCurrentVocabulary(classURI)) {
            addResourceMetadata(classResource, ResourceMetadata.from(classData));
            addClassSpecificMetadata(classResource, classData);
            addSchemeRelationship(classResource, localResourceMap);
            log.debug("Added full metadata for local class: {}", classURI);
        } else {
            log.debug("Skipped full metadata for external class (different namespace): {}", classURI);
        }

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
                                     Map<String, Resource> localResourceMap) {
        log.debug("Transforming {} properties", properties.size());
        for (PropertyData propertyData : properties) {
            try {
                Resource propertyResource = createPropertyResource(propertyData, localResourceMap);
                localPropertyResources.put(propertyData.getName(), propertyResource);
                localResourceMap.put(propertyData.getName(), propertyResource);
                log.debug("Created property: {} -> {}", propertyData.getName(), propertyResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create property: {}", propertyData.getName(), e);
            }
        }
    }

    private Resource createPropertyResource(PropertyData propertyData, Map<String, Resource> localResourceMap) {
        String propertyURI = uriGenerator.generateConceptURI(propertyData.getName(), propertyData.getIdentifier());
        Resource propertyResource = ontModel.createResource(propertyURI);

        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VLASTNOST));

        if (belongsToCurrentVocabulary(propertyURI)) {
            addResourceMetadata(propertyResource, ResourceMetadata.from(propertyData));
            addPropertySpecificMetadata(propertyResource, propertyData);
            addDataGovernanceMetadata(propertyResource, propertyData);
            addSchemeRelationship(propertyResource, localResourceMap);
            log.debug("Added full metadata for local property: {}", propertyURI);
        } else {
            log.debug("Skipped full metadata for external property (different namespace): {}", propertyURI);
        }

        return propertyResource;
    }

    private void addPropertySpecificMetadata(Resource propertyResource, PropertyData propertyData) {
        log.debug("Adding property-specific metadata for: {}", propertyData.getName());

        if (propertyData.getAlternativeName() != null && !propertyData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for property {}: '{}'", propertyData.getName(), propertyData.getAlternativeName());
            addAlternativeNamesAsLangString(propertyResource, propertyData.getAlternativeName());
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
                                        Map<String, Resource> localResourceMap) {
        log.debug("Transforming {} relationships", relationships.size());
        for (RelationshipData relationshipData : relationships) {
            if (!relationshipData.hasValidData()) {
                log.warn("Skipping invalid relationship: {}", relationshipData.getName());
                continue;
            }
            try {
                Resource relationshipResource = createRelationshipResource(relationshipData, localResourceMap);
                localRelationshipResources.put(relationshipData.getName(), relationshipResource);
                localResourceMap.put(relationshipData.getName(), relationshipResource);
                log.debug("Created relationship: {} -> {}", relationshipData.getName(), relationshipResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create relationship: {}", relationshipData.getName(), e);
            }
        }
    }

    private Resource createRelationshipResource(RelationshipData relationshipData, Map<String, Resource> localResourceMap) {
        String relationshipURI = uriGenerator.generateConceptURI(relationshipData.getName(),
                relationshipData.getIdentifier());
        Resource relationshipResource = ontModel.createResource(relationshipURI);

        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VZTAH));
        relationshipResource.addProperty(RDF.type, ontModel.getProperty("http://www.w3.org/2002/07/owl#ObjectProperty"));

        if (belongsToCurrentVocabulary(relationshipURI)) {
            addResourceMetadata(relationshipResource, ResourceMetadata.from(relationshipData));
            addRelationshipSpecificMetadata(relationshipResource, relationshipData);
            addSchemeRelationship(relationshipResource, localResourceMap);
            log.debug("Added full metadata for local relationship: {}", relationshipURI);
        } else {
            log.debug("Skipped full metadata for external relationship (different namespace): {}", relationshipURI);
        }

        addDomainRangeRelationships(relationshipResource, relationshipData);
        return relationshipResource;
    }

    private void addRelationshipSpecificMetadata(Resource relationshipResource, RelationshipData relationshipData) {
        log.debug("Adding relationship-specific metadata for: {}", relationshipData.getName());

        if (relationshipData.getAlternativeName() != null && !relationshipData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for relationship {}: '{}'", relationshipData.getName(), relationshipData.getAlternativeName());
            addAlternativeNamesAsLangString(relationshipResource, relationshipData.getAlternativeName());
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

    private void addResourceMetadata(Resource resource, ResourceMetadata metadata) {
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

        if (metadata.source() != null && !metadata.source().trim().isEmpty()) {
            processSourceField(resource, metadata.source(), true);
        }

        if (metadata.relatedSource() != null && !metadata.relatedSource().trim().isEmpty()) {
            processSourceField(resource, metadata.relatedSource(), false);
        }
    }

    private void processSourceField(Resource resource, String sourceUrls, boolean isDefining) {
        if (sourceUrls.contains(";")) {
            String[] urls = sourceUrls.split(";");
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) {
                    processSingleSource(resource, url.trim(), isDefining);
                }
            }
        } else {
            processSingleSource(resource, sourceUrls, isDefining);
        }
    }

    private void processSingleSource(Resource resource, String url, boolean isDefining) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String trimmedUrl = url.trim();

        if (UtilityMethods.containsEliPattern(trimmedUrl)) {
            handleEliPart(trimmedUrl, resource, isDefining);
        } else {
            handleNonEliPart(trimmedUrl, resource, isDefining);
        }
    }

    private void handleEliPart(String trimmedUrl, Resource resource, boolean isDefining) {
        String eliPart = UtilityMethods.extractEliPart(trimmedUrl);
        if (eliPart != null) {
            String transformedUrl = "https://opendata.eselpoint.cz/esel-esb/" + eliPart;
            String propertyName = isDefining ? DEFINUJICI_USTANOVENI : SOUVISEJICI_USTANOVENI;

            Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);

            if (DataTypeConverter.isUri(transformedUrl)) {
                resource.addProperty(provisionProperty, ontModel.createResource(transformedUrl));
                log.debug("Added {} as URI: {} -> {}", propertyName, trimmedUrl, transformedUrl);
            } else {
                DataTypeConverter.addTypedProperty(resource, provisionProperty, transformedUrl, null, ontModel);
                log.debug("Added {} as literal: {} -> {}", propertyName, trimmedUrl, transformedUrl);
            }
        } else {
            log.warn("Failed to extract ELI part from URL: {}", trimmedUrl);
        }
    }

    private void handleNonEliPart(String trimmedUrl, Resource resource, boolean isDefining) {
        String propertyName = isDefining ? DEFINUJICI_NELEGISLATIVNI_ZDROJ : SOUVISEJICI_NELEGISLATIVNI_ZDROJ;

        String documentUri = uriGenerator.getEffectiveNamespace() + "digitální-dokument-" + System.currentTimeMillis();
        Resource digitalDocument = ontModel.createResource(documentUri);

        Property schemaUrlProperty = ontModel.createProperty("http://schema.org/url");

        if (DataTypeConverter.isUri(trimmedUrl)) {
            digitalDocument.addProperty(schemaUrlProperty, ontModel.createResource(trimmedUrl));
            log.debug("Added schema:url as URI to digital document: {}", trimmedUrl);
        } else {
            DataTypeConverter.addTypedProperty(digitalDocument, schemaUrlProperty, trimmedUrl, null, ontModel);
            log.debug("Added schema:url as literal to digital document: {}", trimmedUrl);
        }

        Property nonLegislativeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);
        resource.addProperty(nonLegislativeProperty, digitalDocument);

        log.debug("Added {} as digital document with schema:url: {}", propertyName, trimmedUrl);
    }

    private void addClassSpecificMetadata(Resource classResource, ClassData classData) {
        addAlternativeName(classResource, classData);
        addEquivalentConcept(classResource, classData);
        addAgenda(classResource, classData);
        addAgendaInformationSystem(classResource, classData);
    }

    private void addAlternativeName(Resource classResource, ClassData classData) {
        if (classData.getAlternativeName() != null && !classData.getAlternativeName().trim().isEmpty()) {
            addAlternativeNamesAsLangString(classResource, classData.getAlternativeName());
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

    private void addAlternativeNamesAsLangString(Resource resource, String altNamesValue) {
        if (altNamesValue == null || altNamesValue.isEmpty()) {
            log.debug("No alternative names to add for resource: {}", resource.getLocalName());
            return;
        }

        log.debug("Processing alternative names as rdf:langString for {}: '{}'", resource.getLocalName(),
                altNamesValue.length() > 100 ? altNamesValue.substring(0, 100) + "..." : altNamesValue);

        Property altNameProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + ALTERNATIVNI_NAZEV);

        if (!altNamesValue.contains(";")) {
            addLangStringLiteral(resource, altNameProperty, altNamesValue);
            log.debug("Added single alternative name as rdf:langString for {}: {}", resource.getLocalName(),
                    altNamesValue.length() > 50 ? altNamesValue.substring(0, 50) + "..." : altNamesValue);
            return;
        }

        String[] altNames = altNamesValue.split(";");
        log.debug("Found {} alternative names separated by semicolons for {}", altNames.length, resource.getLocalName());

        for (String name : altNames) {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                addLangStringLiteral(resource, altNameProperty, trimmedName);
                log.debug("Added alternative name as rdf:langString for {}: {}", resource.getLocalName(),
                        trimmedName.length() > 50 ? trimmedName.substring(0, 50) + "..." : trimmedName);
            }
        }

        log.debug("Finished processing alternative names as rdf:langString for {}", resource.getLocalName());
    }

    private void addLangStringLiteral(Resource resource, Property property, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        try {
            var literal = ontModel.createLiteral(value, com.dia.constants.ExportConstants.Common.DEFAULT_LANG);
            resource.addProperty(property, literal);

            log.debug("Added rdf:langString literal: '{}' with language '{}'", value, com.dia.constants.ExportConstants.Common.DEFAULT_LANG);
        } catch (Exception e) {
            log.warn("Failed to create rdf:langString literal for value '{}': {}. Using regular language-tagged literal instead.",
                    value, e.getMessage());
            resource.addProperty(property, value, com.dia.constants.ExportConstants.Common.DEFAULT_LANG);
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
                propertyResource.addProperty(rangeProperty, ontModel.createLiteral(dataType));
                log.debug("Added unknown data type '{}' as plain string literal", dataType);
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
            handlePublicData(propertyResource, propertyData, isPublicValue, privacyProvision);
        }
    }

    private void handleNonPublicData(Resource propertyResource, PropertyData propertyData, String privacyProvision) {
        propertyResource.addProperty(RDF.type,
                ontModel.getResource(OFN_NAMESPACE + NEVEREJNY_UDAJ));

        log.debug("Added non-public data type for concept: {}", propertyData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddPrivacyProvision(propertyResource, propertyData, privacyProvision);
        }
    }

    private void handlePublicData(Resource propertyResource, PropertyData propertyData, String isPublicValue, String privacyProvision) {
        if (UtilityMethods.isBooleanValue(isPublicValue)) {
            Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

            if (Boolean.TRUE.equals(isPublic)) {
                if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                    log.warn("Concept '{}' marked as public but has privacy provision '{}' - treating as non-public",
                            propertyData.getName(), privacyProvision);
                    handleNonPublicData(propertyResource, propertyData, privacyProvision);
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
                handleNonPublicData(propertyResource, propertyData, privacyProvision);
            }
        } else {
            log.warn("Unrecognized boolean value for public property: '{}' for concept '{}'",
                    isPublicValue, propertyData.getName());
        }
    }

    private void validateAndAddPrivacyProvision(Resource propertyResource, PropertyData propertyData, String provision) {
        String trimmedProvision = provision.trim();

        if (UtilityMethods.containsEliPattern(trimmedProvision)) {
            String eliPart = UtilityMethods.extractEliPart(trimmedProvision);
            if (eliPart != null) {
                String transformedProvision = "https://opendata.eselpoint.cz/esel-esb/" + eliPart;
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
                log.warn("Failed to extract ELI part from privacy provision for concept '{}': '{}'",
                        propertyData.getName(), trimmedProvision);
            }
        } else {
            log.debug("Privacy provision does not contain ELI pattern for concept '{}': '{}' - skipping",
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