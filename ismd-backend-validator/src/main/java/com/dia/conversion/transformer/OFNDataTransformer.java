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
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.*;

import com.dia.constants.DataTypeConstants;
import com.dia.constants.FormatConstants;
import com.dia.constants.ExportConstants;
import static com.dia.constants.VocabularyConstants.*;
import static com.dia.constants.ExportConstants.Common.*;
import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;

@Deprecated
@Component
@Slf4j
@Getter
public class OFNDataTransformer {

    private OntModel ontModel;
    private final URIGenerator uriGenerator;
    private final ConceptFilterUtil conceptFilterUtil;
    private static final Map<String, String> CZECH_TO_XSD_MAPPING = createDataTypeMapping();

    private Map<String, Resource> allClassResourcesForHierarchies;

    private static Map<String, String> createDataTypeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("Ano či ne", DataTypeConstants.XSD_BOOLEAN);
        mapping.put("Datum", DataTypeConstants.XSD_DATE);
        mapping.put("Čas", DataTypeConstants.XSD_TIME);
        mapping.put("Datum a čas", DataTypeConstants.XSD_DATETIME_STAMP);
        mapping.put("Celé číslo", DataTypeConstants.XSD_INTEGER);
        mapping.put("Desetinné číslo", DataTypeConstants.XSD_DOUBLE);
        mapping.put("URI, IRI, URL", DataTypeConstants.XSD_ANY_URI);
        mapping.put("Řetězec", DataTypeConstants.XSD_STRING);
        mapping.put("Text", DataTypeConstants.RDFS_LITERAL);
        return Collections.unmodifiableMap(mapping);
    }

    public OFNDataTransformer(ConceptFilterUtil conceptFilterUtil) {
        this.uriGenerator = new URIGenerator();
        this.conceptFilterUtil = conceptFilterUtil;
    }

    public TransformationResult transform(OntologyData ontologyData) throws ConversionException {
        try {
            log.info("Starting ontology data transformation with temporal support...");

            if (ontologyData == null || ontologyData.getVocabularyMetadata() == null) {
                throw new ConversionException("Invalid ontology data");
            }

            ConceptFilterUtil.FilterStatistics filterStatistics = new ConceptFilterUtil.FilterStatistics();
            Map<String, String> nameToIdentifierMap = conceptFilterUtil.buildNameToIdentifierMap(ontologyData);
            conceptFilterUtil.buildFilteredConceptSet(ontologyData, filterStatistics);


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

            transformClasses(ontologyData.getClasses(), localClassResources, localResourceMap, nameToIdentifierMap, filterStatistics);
            transformProperties(ontologyData.getProperties(), localPropertyResources, localResourceMap, nameToIdentifierMap, filterStatistics);
            transformRelationships(ontologyData.getRelationships(), localRelationshipResources, localResourceMap, nameToIdentifierMap, filterStatistics);
            transformHierarchies(ontologyData.getHierarchies(), localClassResources, localPropertyResources, localResourceMap, nameToIdentifierMap, filterStatistics);

            Map<String, String> modelProperties = createModelProperties(ontologyData.getVocabularyMetadata());

            log.info("Transformation completed successfully. Created {} classes, {} properties, {} relationships",
                    localClassResources.size(), localPropertyResources.size(), localRelationshipResources.size());

            if (conceptFilterUtil.getConceptFilterPattern() != null) {
                filterStatistics.logStatistics();
            }

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

        for (ClassData classData : ontologyData.getClasses()) {
            if (hasPublicDataClassification(classData)) {
                requiredClasses.add(VEREJNY_UDAJ);
            }
            if (hasPrivateDataClassification(classData)) {
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

    private Set<String> analyzeRequiredProperties(OntologyData ontologyData) {
        RequiredPropertiesAnalyzer analyzer = new RequiredPropertiesAnalyzer(ontologyData);
        return analyzer.analyze();
    }

    private boolean hasPublicDataClassification(ClassData classData) {
        return classData.getIsPublic() != null &&
                (classData.getIsPublic().toLowerCase().contains("ano") ||
                        classData.getIsPublic().toLowerCase().contains("true") ||
                        classData.getIsPublic().equalsIgnoreCase("yes"));
    }

    private boolean hasPrivateDataClassification(ClassData classData) {
        return (classData.getIsPublic() != null &&
                (classData.getIsPublic().toLowerCase().contains("ne") ||
                        classData.getIsPublic().toLowerCase().contains("false") ||
                        classData.getIsPublic().equalsIgnoreCase("no"))) ||
                (classData.getPrivacyProvision() != null && !classData.getPrivacyProvision().trim().isEmpty());
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
                                  Map<String, Resource> localResourceMap, Map<String, String> nameToIdentifierMap,
                                  ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Transforming {} classes", classes.size());

        Map<String, Resource> allClassResources = new HashMap<>();

        for (ClassData classData : classes) {
            if (!classData.hasValidData()) {
                log.warn("Skipping invalid class: {}", classData.getName());
                continue;
            }

            String identifier = classData.getIdentifier();

            if (conceptFilterUtil.shouldFilterConcept(identifier)) {
                log.info("FILTERED: Class '{}' with identifier '{}' matches filter regex",
                        classData.getName(), identifier);
                filterStatistics.filteredClasses++;
                continue;
            }

            String classURI = uriGenerator.generateConceptURI(classData.getName(), identifier);
            log.debug("Processing class '{}' with identifier='{}' -> URI={}",
                classData.getName(), identifier, classURI);

            try {
                if (classData.getSuperClass() != null &&
                        conceptFilterUtil.isConceptFiltered(classData.getSuperClass(), nameToIdentifierMap)) {
                    log.info("OMITTED: superClass '{}' for class '{}' (superClass is filtered)",
                            classData.getSuperClass(), classData.getName());
                    classData.setSuperClass(null);
                    filterStatistics.omittedSuperClasses++;
                }

                Resource classResource = createClassResource(classData, localResourceMap, filterStatistics);

                allClassResources.put(classData.getName(), classResource);

                if (belongsToCurrentVocabulary(classURI)) {
                    localClassResources.put(classData.getName(), classResource);
                    localResourceMap.put(classData.getName(), classResource);
                    log.debug("Created local class: {} -> {}", classData.getName(), classResource.getURI());
                } else {
                    localResourceMap.put(classData.getName(), classResource);
                    log.info("Created external concept (for relationships only): '{}' with identifier='{}' -> URI={}",
                        classData.getName(), identifier, classURI);
                }
            } catch (Exception e) {
                log.error("Failed to create class: {}", classData.getName(), e);
            }
        }

        this.allClassResourcesForHierarchies = allClassResources;
    }

    private Resource createClassResource(ClassData classData, Map<String, Resource> localResourceMap, ConceptFilterUtil.FilterStatistics filterStatistics) {
        String classURI = uriGenerator.generateConceptURI(classData.getName(), classData.getIdentifier());
        Resource classResource = ontModel.createResource(classURI);

        classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        addSpecificClassType(classResource, classData);

        if (belongsToCurrentVocabulary(classURI)) {
            addResourceMetadata(classResource, ResourceMetadata.from(classData));
            addClassSpecificMetadata(classResource, classData, filterStatistics);
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
                                     Map<String, Resource> localResourceMap, Map<String, String> nameToIdentifierMap,
                                     ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Transforming {} properties", properties.size());
        for (PropertyData propertyData : properties) {
            if (conceptFilterUtil.shouldFilterConcept(propertyData.getIdentifier())) {
                log.info("FILTERED: Property '{}' with identifier '{}' matches filter regex",
                        propertyData.getName(), propertyData.getIdentifier());
                filterStatistics.filteredProperties++;
                continue;
            }

            if (propertyData.getDomain() != null &&
                    conceptFilterUtil.isConceptFiltered(propertyData.getDomain(), nameToIdentifierMap)) {
                log.info("FILTERED: Property '{}' because its domain '{}' is filtered",
                        propertyData.getName(), propertyData.getDomain());
                filterStatistics.filteredProperties++;
                filterStatistics.omittedDomains++;
                continue;
            }

            if (propertyData.getSuperProperty() != null &&
                    conceptFilterUtil.isConceptFiltered(propertyData.getSuperProperty(), nameToIdentifierMap)) {
                log.info("OMITTED: superProperty '{}' for property '{}' (superProperty is filtered)",
                        propertyData.getSuperProperty(), propertyData.getName());
                propertyData.setSuperProperty(null);
                filterStatistics.omittedSuperProperties++;
            }

            try {
                Resource propertyResource = createPropertyResource(propertyData, localResourceMap, filterStatistics);
                localPropertyResources.put(propertyData.getName(), propertyResource);
                localResourceMap.put(propertyData.getName(), propertyResource);
                log.debug("Created property: {} -> {}", propertyData.getName(), propertyResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create property: {}", propertyData.getName(), e);
            }
        }
    }

    private Resource createPropertyResource(PropertyData propertyData, Map<String, Resource> localResourceMap, ConceptFilterUtil.FilterStatistics filterStatistics) {
        String propertyURI = uriGenerator.generateConceptURI(propertyData.getName(), propertyData.getIdentifier());

        OntProperty propertyResource;
        if (isObjectProperty(propertyData)) {
            propertyResource = ontModel.createObjectProperty(propertyURI);
        } else {
            propertyResource = ontModel.createDatatypeProperty(propertyURI);
        }

        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VLASTNOST));

        if (belongsToCurrentVocabulary(propertyURI)) {
            addResourceMetadata(propertyResource, ResourceMetadata.from(propertyData));
            addPropertySpecificMetadata(propertyResource, propertyData, localResourceMap, filterStatistics);
            addPropertySuperPropertyRelationship(propertyResource, propertyData);
        }

        return propertyResource;
    }

    private void addPropertySpecificMetadata(Resource propertyResource, PropertyData propertyData, Map<String, Resource> localResourceMap, ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Adding property-specific metadata for: {}", propertyData.getName());

        if (propertyData.getAlternativeName() != null && !propertyData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for property {}: '{}'", propertyData.getName(), propertyData.getAlternativeName());
            addAlternativeNamesAsLangString(propertyResource, propertyData.getAlternativeName());
        }

        if (propertyData.getEquivalentConcept() != null && !propertyData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for property {}: '{}'", propertyData.getName(), propertyData.getEquivalentConcept());
            addEquivalentConcept(propertyResource, propertyData.getEquivalentConcept(), "property", filterStatistics);
        }

        if (propertyData.getDomain() != null && !propertyData.getDomain().trim().isEmpty()) {
            addResourceReference(propertyResource, RDFS.domain, propertyData.getDomain(), localResourceMap);
        }

        addPropertyPPDFData(propertyResource, propertyData);

        addRangeInformation(propertyResource, propertyData);

        addPropertyDataGovernanceMetadata(propertyResource, propertyData);
    }

    private void addPropertySuperPropertyRelationship(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getSuperProperty() != null && !propertyData.getSuperProperty().trim().isEmpty()) {
            String superProperty = propertyData.getSuperProperty().trim();

            String superPropertyURI;
            if (DataTypeConverter.isUri(superProperty)) {
                superPropertyURI = superProperty;
            } else {
                superPropertyURI = uriGenerator.generateConceptURI(superProperty, null);
            }

            propertyResource.addProperty(RDFS.subPropertyOf, ontModel.createResource(superPropertyURI));

            log.debug("Added subPropertyOf relationship: {} -> {}", propertyResource.getURI(), superPropertyURI);
        }
    }

    private void transformRelationships(List<RelationshipData> relationships, Map<String, Resource> localRelationshipResources,
                                        Map<String, Resource> localResourceMap,  Map<String, String> nameToIdentifierMap,
                                        ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Transforming {} relationships", relationships.size());
        for (RelationshipData relationshipData : relationships) {
            if (conceptFilterUtil.shouldFilterConcept(relationshipData.getIdentifier())) {
                log.info("FILTERED: Relationship '{}' with identifier '{}' matches filter regex",
                        relationshipData.getName(), relationshipData.getIdentifier());
                filterStatistics.filteredRelationships++;
                continue;
            }

            if (relationshipData.getDomain() != null &&
                    conceptFilterUtil.isConceptFiltered(relationshipData.getDomain(), nameToIdentifierMap)) {
                log.info("FILTERED: Relationship '{}' because its domain '{}' is filtered",
                        relationshipData.getName(), relationshipData.getDomain());
                filterStatistics.filteredRelationships++;
                filterStatistics.omittedDomains++;
                continue;
            }

            if (relationshipData.getRange() != null &&
                    conceptFilterUtil.isConceptFiltered(relationshipData.getRange(), nameToIdentifierMap)) {
                log.info("FILTERED: Relationship '{}' because its range '{}' is filtered",
                        relationshipData.getName(), relationshipData.getRange());
                filterStatistics.filteredRelationships++;
                filterStatistics.omittedRanges++;
                continue;
            }

            if (relationshipData.getSuperRelation() != null &&
                    conceptFilterUtil.isConceptFiltered(relationshipData.getSuperRelation(), nameToIdentifierMap)) {
                log.info("OMITTED: superRelation '{}' for relationship '{}' (superRelation is filtered)",
                        relationshipData.getSuperRelation(), relationshipData.getName());
                relationshipData.setSuperRelation(null);
                filterStatistics.omittedSuperRelations++;
            }

            try {
                Resource relationshipResource = createRelationshipResource(relationshipData, localResourceMap, filterStatistics);
                localRelationshipResources.put(relationshipData.getName(), relationshipResource);
                localResourceMap.put(relationshipData.getName(), relationshipResource);
                log.debug("Created relationship: {} -> {}", relationshipData.getName(), relationshipResource.getURI());
            } catch (Exception e) {
                log.error("Failed to create relationship: {}", relationshipData.getName(), e);
            }
        }
    }

    private Resource createRelationshipResource(RelationshipData relationshipData, Map<String, Resource> localResourceMap, ConceptFilterUtil.FilterStatistics filterStatistics) {
        String relationshipURI = uriGenerator.generateConceptURI(relationshipData.getName(),
                relationshipData.getIdentifier());

        OntProperty relationshipResource = ontModel.createObjectProperty(relationshipURI);
        relationshipResource.addProperty(RDF.type, OWL2.ObjectProperty);
        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + POJEM));
        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VZTAH));

        log.debug("Created ObjectProperty for relationship: {} -> {}", relationshipData.getName(), relationshipURI);

        if (belongsToCurrentVocabulary(relationshipURI)) {
            addResourceMetadata(relationshipResource, ResourceMetadata.from(relationshipData));
            addRelationshipSpecificMetadata(relationshipResource, relationshipData, filterStatistics);
            addRelationshipSuperPropertyRelationship(relationshipResource, relationshipData);
            log.debug("Added full metadata for local relationship: {}", relationshipURI);
        } else {
            log.debug("Skipped full metadata for external relationship (different namespace): {}", relationshipURI);
        }

        addDomainRangeRelationships(relationshipResource, relationshipData, localResourceMap);
        return relationshipResource;
    }

    private void addRelationshipSpecificMetadata(Resource relationshipResource, RelationshipData relationshipData, ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Adding relationship-specific metadata for: {}", relationshipData.getName());

        if (relationshipData.getAlternativeName() != null && !relationshipData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for relationship {}: '{}'", relationshipData.getName(), relationshipData.getAlternativeName());
            addAlternativeNamesAsLangString(relationshipResource, relationshipData.getAlternativeName());
        } else {
            log.debug("No alternative names found for relationship: {}", relationshipData.getName());
        }

        if (relationshipData.getEquivalentConcept() != null && !relationshipData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for relationship {}: '{}'", relationshipData.getName(), relationshipData.getEquivalentConcept());
            addEquivalentConcept(relationshipResource, relationshipData.getEquivalentConcept(), "relationship", filterStatistics);
        }

        addRelationshipPPDFData(relationshipResource, relationshipData);
        addRelationshipAgenda(relationshipResource, relationshipData);
        addRelationshipAgendaInformationSystem(relationshipResource, relationshipData);
        addRelationshipDataGovernanceMetadata(relationshipResource, relationshipData);
    }

    private void addRelationshipSuperPropertyRelationship(Resource relationshipResource, RelationshipData relationshipData) {
        if (relationshipData.getSuperRelation() != null && !relationshipData.getSuperRelation().trim().isEmpty()) {
            String superRelation = relationshipData.getSuperRelation().trim();

            String superRelationURI;
            if (DataTypeConverter.isUri(superRelation)) {
                superRelationURI = superRelation;
            } else {
                superRelationURI = uriGenerator.generateConceptURI(superRelation, null);
            }

            relationshipResource.addProperty(RDFS.subPropertyOf, ontModel.createResource(superRelationURI));

            log.debug("Added subPropertyOf relationship: {} -> {}", relationshipResource.getURI(), superRelationURI);
        }
    }

    private void transformHierarchies(List<HierarchyData> hierarchies, Map<String, Resource> localClassResources,
                                      Map<String, Resource> localPropertyResources, Map<String, Resource> localResourceMap,
                                      Map<String, String> nameToIdentifierMap, ConceptFilterUtil.FilterStatistics filterStats) {
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

            if (conceptFilterUtil.isConceptFiltered(hierarchyData.getSubClass(), nameToIdentifierMap)) {
                log.info("FILTERED: Hierarchy '{}' IS-A '{}' because subClass is filtered",
                        hierarchyData.getSubClass(), hierarchyData.getSuperClass());
                filterStats.filteredHierarchies++;
                skippedHierarchies++;
                continue;
            }

            if (conceptFilterUtil.isConceptFiltered(hierarchyData.getSuperClass(), nameToIdentifierMap)) {
                log.info("FILTERED: Hierarchy '{}' IS-A '{}' because superClass is filtered",
                        hierarchyData.getSubClass(), hierarchyData.getSuperClass());
                filterStats.filteredHierarchies++;
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

        if (allClassResourcesForHierarchies != null) {
            resource = allClassResourcesForHierarchies.get(className);
            if (resource != null) {
                log.debug("Found resource in all class resources map (including external): {}", className);
                return resource;
            }
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
        if (sourceUrls == null || sourceUrls.trim().isEmpty()) {
            log.debug("Skipping empty source field for resource: {}", resource.getLocalName());
            return;
        }

        String trimmedUrls = sourceUrls.trim();
        log.debug("Processing source field for resource {}: '{}'", resource.getLocalName(),
                trimmedUrls.length() > 100 ? trimmedUrls.substring(0, 100) + "..." : trimmedUrls);

        if (trimmedUrls.contains(";")) {
            String[] urls = trimmedUrls.split(";");
            log.debug("Found {} sources separated by semicolons for resource: {}", urls.length, resource.getLocalName());

            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (!url.isEmpty()) {
                    log.debug("Processing source part {}/{}: '{}'", i+1, urls.length, url);
                    processSingleSource(resource, url, isDefining);
                }
            }
        } else {
            processSingleSource(resource, trimmedUrls, isDefining);
        }
    }

    private void processSingleSource(Resource resource, String url, boolean isDefining) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String trimmedUrl = url.trim();
        log.debug("Processing single source for resource {}: '{}'", resource.getLocalName(), trimmedUrl);

        if (UtilityMethods.containsEliPattern(trimmedUrl)) {
            handleEliPart(trimmedUrl, resource, isDefining);
            log.debug("Processed as ELI (legislative) source: {}", trimmedUrl);
        } else {
            handleNonEliPart(trimmedUrl, resource, isDefining);
            log.debug("Processed as non-ELI (non-legislative) source: {}", trimmedUrl);
        }
    }

    private void handleEliPart(String trimmedUrl, Resource resource, boolean isDefining) {
        String eliPart = UtilityMethods.extractEliPart(trimmedUrl);
        if (eliPart != null) {
            String transformedUrl = "https://opendata.eselpoint.cz/esel-esb/" + eliPart;
            String propertyName = isDefining ? DEFINUJICI_USTANOVENI : SOUVISEJICI_USTANOVENI;

            Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);

            if (UtilityMethods.isValidUrl(transformedUrl)) {
                resource.addProperty(provisionProperty, ontModel.createResource(transformedUrl));
                log.debug("Added {} as URI: {} -> {}", propertyName, trimmedUrl, transformedUrl);
            } else {
                log.debug("Skipped {}, invalid URL value: {} -> {}", propertyName, trimmedUrl, transformedUrl);
            }
        } else {
            log.warn("Failed to extract ELI part from URL: {}", trimmedUrl);
            handleNonEliPart(trimmedUrl, resource, isDefining);
        }
    }

    private void handleNonEliPart(String trimmedUrl, Resource resource, boolean isDefining) {
        String propertyName = isDefining ? DEFINUJICI_NELEGISLATIVNI_ZDROJ : SOUVISEJICI_NELEGISLATIVNI_ZDROJ;

        Resource digitalDocument = ontModel.createResource();

        digitalDocument.addProperty(RDF.type, ontModel.createResource("https://slovník.gov.cz/generický/digitální-objekty/pojem/digitální-objekt"));

        if (UtilityMethods.isValidUrl(trimmedUrl)) {
            Property schemaUrlProperty = ontModel.createProperty("http://schema.org/url");
            Literal urlLiteral = ontModel.createTypedLiteral(trimmedUrl, "http://www.w3.org/2001/XMLSchema#anyURI");
            digitalDocument.addProperty(schemaUrlProperty, urlLiteral);
            log.debug("Added digital document with schema:url (xsd:anyURI): {}", trimmedUrl);
        } else {
            Property dctermsTitle = ontModel.createProperty("http://purl.org/dc/terms/title");
            digitalDocument.addProperty(dctermsTitle, trimmedUrl, DEFAULT_LANG);
            log.debug("Added digital document with dcterms:title (@cs): {}", trimmedUrl);
        }

        Property nonLegislativeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);
        resource.addProperty(nonLegislativeProperty, digitalDocument);

        log.debug("Added as digital document: {}", propertyName);
    }

    private void addClassSpecificMetadata(Resource classResource, ClassData classData, ConceptFilterUtil.FilterStatistics filterStatistics) {
        addAlternativeName(classResource, classData);
        addEquivalentConcept(classResource, classData, filterStatistics);
        addAgenda(classResource, classData);
        addAgendaInformationSystem(classResource, classData);
        addClassDataGovernanceMetadata(classResource, classData);
    }

    private void addAlternativeName(Resource classResource, ClassData classData) {
        if (classData.getAlternativeName() != null && !classData.getAlternativeName().trim().isEmpty()) {
            addAlternativeNamesAsLangString(classResource, classData.getAlternativeName());
        }
    }

    private void addEquivalentConcept(Resource classResource, ClassData classData, ConceptFilterUtil.FilterStatistics filterStatistics) {
        if (classData.getEquivalentConcept() != null && !classData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for class {}: '{}'", classData.getName(), classData.getEquivalentConcept());
            addEquivalentConcept(classResource, classData.getEquivalentConcept(), "class", filterStatistics);
        }
    }

    private void addEquivalentConcept(Resource resource, String equivalentConcept, String entityType, ConceptFilterUtil.FilterStatistics filterStatistics) {
        if (!UtilityMethods.isValidIRI(equivalentConcept)) {
            log.warn("Skipping invalid equivalent concept for {} '{}': '{}' is not a valid IRI",
                    entityType, resource.getLocalName(), equivalentConcept);
            return;
        }

        if (conceptFilterUtil.shouldFilterConcept(equivalentConcept)) {
            log.info("OMITTED: equivalentConcept '{}' for {} '{}' (equivalentConcept is filtered)",
                    equivalentConcept, entityType, resource.getLocalName());
            filterStatistics.omittedEquivalentConcepts++;
            return;
        }

        Property exactMatchProperty = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch");
        resource.addProperty(exactMatchProperty, ontModel.createResource(equivalentConcept));
        log.debug("Added valid equivalent concept IRI for {} '{}': {}",
                entityType, resource.getLocalName(), equivalentConcept);
    }

    private void addAgenda(Resource classResource, ClassData classData) {
        addAgenda(classResource, classData.getAgendaCode(), "class", classData.getName());
    }

    private void addAgenda(Resource resource, String agendaCode, String entityType, String entityName) {
        if (agendaCode != null && !agendaCode.trim().isEmpty()) {
            String trimmedCode = agendaCode.trim();

            if (UtilityMethods.isValidAgendaValue(trimmedCode)) {
                String transformedAgenda = UtilityMethods.transformAgendaValue(trimmedCode);
                Property agendaProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + AGENDA);

                if (DataTypeConverter.isUri(transformedAgenda)) {
                    resource.addProperty(agendaProperty, ontModel.createResource(transformedAgenda));
                    log.debug("Added valid agenda as URI for {}: {} -> {}", entityType, trimmedCode, transformedAgenda);
                } else {
                    DataTypeConverter.addTypedProperty(resource, agendaProperty, transformedAgenda, null, ontModel);
                    log.debug("Added valid agenda as literal for {}: {} -> {}", entityType, trimmedCode, transformedAgenda);
                }
            } else {
                log.warn("Invalid agenda code '{}' for {} '{}' - skipping", trimmedCode, entityType, entityName);
            }
        }
    }

    private void addAgendaInformationSystem(Resource classResource, ClassData classData) {
        addAgendaInformationSystem(classResource, classData.getAgendaSystemCode(), "class", classData.getName());
    }

    private void addAgendaInformationSystem(Resource resource, String aisCode, String entityType, String entityName) {
        if (aisCode != null && !aisCode.trim().isEmpty()) {
            String trimmedCode = aisCode.trim();

            if (UtilityMethods.isValidAISValue(trimmedCode)) {
                String transformedAIS = UtilityMethods.transformAISValue(trimmedCode);
                Property aisProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + AIS);

                if (DataTypeConverter.isUri(transformedAIS)) {
                    resource.addProperty(aisProperty, ontModel.createResource(transformedAIS));
                    log.debug("Added valid AIS as URI for {}: {} -> {}", entityType, trimmedCode, transformedAIS);
                } else {
                    DataTypeConverter.addTypedProperty(resource, aisProperty, transformedAIS, null, ontModel);
                    log.debug("Added valid AIS as literal for {}: {} -> {}", entityType, trimmedCode, transformedAIS);
                }
            } else {
                log.warn("Invalid AIS code '{}' for {} '{}' - skipping", trimmedCode, entityType, entityName);
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
            var literal = ontModel.createLiteral(value, DEFAULT_LANG);
            resource.addProperty(property, literal);

            log.debug("Added rdf:langString literal: '{}' with language '{}'", value, DEFAULT_LANG);
        } catch (Exception e) {
            log.warn("Failed to create rdf:langString literal for value '{}': {}. Using regular language-tagged literal instead.",
                    value, e.getMessage());
            resource.addProperty(property, value, DEFAULT_LANG);
        }
    }

    private void addRangeInformation(Resource propertyResource, PropertyData propertyData) {
        String dataType = propertyData.getDataType();
        Property rangeProperty = RDFS.range;

        if (dataType == null || dataType.trim().isEmpty()) {
            propertyResource.addProperty(rangeProperty, ontModel.createResource(DataTypeConstants.RDFS_LITERAL));
            log.debug("Added default rdfs:Literal range type for property without data type specification");
            return;
        }

        String trimmedDataType = dataType.trim();
        log.debug("Processing data type for property '{}': '{}'", propertyData.getName(), trimmedDataType);

        if (checkIfXsdType(trimmedDataType, rangeProperty, propertyResource)) {
            return;
        }
        if (checkIfFullXsdUri(trimmedDataType, rangeProperty, propertyResource)) {
            return;
        }
        if (checkIfValidUri(trimmedDataType, rangeProperty, propertyResource)) {
            return;
        }
        if (checkCzechDataType(trimmedDataType, rangeProperty, propertyResource)) {
            return;
        }

        String detectedType = tryDetectDataTypeFromValue(trimmedDataType);
        if (detectedType != null) {
            propertyResource.addProperty(rangeProperty, ontModel.createResource(detectedType));
            log.debug("Detected data type '{}' for input '{}' using DataTypeConverter", detectedType, trimmedDataType);
            return;
        }

        propertyResource.addProperty(rangeProperty, ontModel.createResource(DataTypeConstants.RDFS_LITERAL));
        log.debug("Unrecognized data type '{}' - using rdfs:Literal as fallback", trimmedDataType);
    }

    private boolean checkIfXsdType(String trimmedDataType, Property rangeProperty, Resource propertyResource) {
        if (trimmedDataType.startsWith("xsd:")) {
            String localName = trimmedDataType.substring(4);
            if (DataTypeConverter.isValidXSDType(localName)) {
                String xsdType = ExportConstants.Json.XSD_NS + localName;
                propertyResource.addProperty(rangeProperty, ontModel.createResource(xsdType));
                log.debug("Added valid XSD range type: {}", xsdType);
                return true;
            } else {
                log.warn("Invalid XSD type '{}' - falling back to rdfs:Literal", trimmedDataType);
                propertyResource.addProperty(rangeProperty, ontModel.createResource(DataTypeConstants.RDFS_LITERAL));
                return true;
            }
        }
        return false;
    }

    private boolean checkIfFullXsdUri(String trimmedDataType, Property rangeProperty, Resource propertyResource) {
        if (trimmedDataType.startsWith(ExportConstants.Json.XSD_NS)) {
            String localName = trimmedDataType.substring(ExportConstants.Json.XSD_NS.length());
            if (DataTypeConverter.isValidXSDType(localName)) {
                propertyResource.addProperty(rangeProperty, ontModel.createResource(trimmedDataType));
                log.debug("Added valid full XSD URI range type: {}", trimmedDataType);
                return true;
            } else {
                log.warn("Invalid XSD URI '{}' - falling back to rdfs:Literal", trimmedDataType);
                propertyResource.addProperty(rangeProperty, ontModel.createResource(DataTypeConstants.RDFS_LITERAL));
                return true;
            }
        }
        return false;
    }

    private boolean checkIfValidUri(String trimmedDataType, Property rangeProperty, Resource propertyResource) {
        if (DataTypeConverter.isUri(trimmedDataType)) {
            propertyResource.addProperty(rangeProperty, ontModel.createResource(trimmedDataType));
            log.debug("Added URI range type: {}", trimmedDataType);
            return true;
        }
        return false;
    }

    private boolean checkCzechDataType(String trimmedDataType, Property rangeProperty, Resource propertyResource) {
        String mappedXsdType = CZECH_TO_XSD_MAPPING.get(trimmedDataType);
        if (mappedXsdType != null) {
            propertyResource.addProperty(rangeProperty, ontModel.createResource(mappedXsdType));
            log.debug("Mapped Czech data type '{}' to XSD type: {}", trimmedDataType, mappedXsdType);
            return true;
        }

        for (Map.Entry<String, String> entry : CZECH_TO_XSD_MAPPING.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedDataType)) {
                propertyResource.addProperty(rangeProperty, ontModel.createResource(entry.getValue()));
                log.debug("Mapped Czech data type '{}' (case-insensitive) to XSD type: {}", trimmedDataType, entry.getValue());
                return true;
            }
        }
        return false;
    }

    private String tryDetectDataTypeFromValue(String value) {
        if (DataTypeConverter.isBooleanValue(value)) {
            return DataTypeConstants.XSD_BOOLEAN;
        }
        if (DataTypeConverter.isInteger(value)) {
            return DataTypeConstants.XSD_INTEGER;
        }
        if (DataTypeConverter.isDouble(value)) {
            return DataTypeConstants.XSD_DOUBLE;
        }
        if (DataTypeConverter.isDate(value)) {
            return DataTypeConstants.XSD_DATE;
        }
        if (DataTypeConverter.isTime(value)) {
            return DataTypeConstants.XSD_TIME;
        }
        if (DataTypeConverter.isDateTime(value)) {
            return DataTypeConstants.XSD_DATETIME_STAMP;
        }
        if (DataTypeConverter.isDateTimeStamp(value)) {
            return DataTypeConstants.XSD_DATETIME_STAMP;
        }

        return null;
    }


    private void handleGovernanceProperty(Resource resource, String value, String propertyType) {
        if (value == null || value.trim().isEmpty()) {
            log.debug("Skipping empty governance property '{}' for resource: {}", propertyType, resource.getLocalName());
            return;
        }

        List<String> allowedValues = List.of("veřejně přístupné", "poskytované na žádost", "nesdílené", "zpřístupňované pro výkon agendy", "základních registrů", "jiných agend", "vlastní", "provozní", "identifikační", "evidenční", "statistické");
        if (!allowedValues.contains(value.toLowerCase())) {
            log.debug("Skipping unsupported governance property value '{}' for resource: {}", propertyType, resource.getLocalName());
            return;
        }

        String propertyConstant = getGovernancePropertyConstant(propertyType);

        log.debug("Using constant '{}' for governance property type '{}'", propertyConstant, propertyType);
        addGovernanceProperty(resource, value, propertyConstant);
    }

    private String getGovernancePropertyConstant(String propertyType) {
        return switch (propertyType) {
            case "sharing-method" -> getConstantValue(FormatConstants.Excel.ZPUSOB_SDILENI_UDEJE, ZPUSOB_SDILENI, "způsob-sdílení-údajů");
            case "acquisition-method" -> getConstantValue(FormatConstants.Excel.ZPUSOB_ZISKANI_UDEJE, ZPUSOB_ZISKANI, "způsob-získání-údajů");
            case "content-type" -> getConstantValue(FormatConstants.Excel.TYP_OBSAHU_UDAJE, TYP_OBSAHU, "typ-obsahu-údajů");
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
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String trimmedValue = value.trim();
        Property property = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);

        String governanceIRI = transformToGovernanceIRI(propertyName, trimmedValue);

        if (governanceIRI != null) {
            resource.addProperty(property, ontModel.createResource(governanceIRI));
            log.debug("Added governance property {} as governance IRI: {} -> {}", propertyName, trimmedValue, governanceIRI);
        } else if (DataTypeConverter.isUri(trimmedValue)) {
            resource.addProperty(property, ontModel.createResource(trimmedValue));
            log.debug("Added governance property {} as URI: {}", propertyName, trimmedValue);
        } else {
            DataTypeConverter.addTypedProperty(resource, property, trimmedValue, null, ontModel);
            log.debug("Added governance property {} as typed literal: {}", propertyName, trimmedValue);
        }
    }

    private String transformToGovernanceIRI(String propertyName, String value) {
        if (propertyName == null || value == null) {
            return null;
        }

        String sanitizedValue = UtilityMethods.sanitizeForIRI(value);

        return switch (propertyName) {
            case "typ-obsahu-údajů" ->
                    "https://data.dia.gov.cz/zdroj/číselníky/typy-obsahu-údajů/položky/" + sanitizedValue;
            case "způsob-sdílení-údajů" ->
                    "https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/" + sanitizedValue;
            case "způsob-získání-údajů" ->
                    "https://data.dia.gov.cz/zdroj/číselníky/způsoby-získání-údajů/položky/" + sanitizedValue;
            default -> null;
        };
    }

    private void addDomainRangeRelationships(Resource relationshipResource, RelationshipData relationshipData, Map<String, Resource> localResourceMap) {
        if (relationshipData.getDomain() != null && !relationshipData.getDomain().trim().isEmpty()) {
            addResourceReference(relationshipResource, RDFS.domain, relationshipData.getDomain(), localResourceMap);
        }

        if (relationshipData.getRange() != null && !relationshipData.getRange().trim().isEmpty()) {
            addResourceReference(relationshipResource, RDFS.range, relationshipData.getRange(), localResourceMap);
        }
    }

    private void addResourceReference(Resource subject, Property property, String referenceName, Map<String, Resource> resourceMap) {
        if (DataTypeConverter.isUri(referenceName)) {
            subject.addProperty(property, ontModel.createResource(referenceName));
            log.debug("Added URI resource reference: {} -> {}", property.getLocalName(), referenceName);
        } else {
            if (property.equals(RDFS.domain) || property.equals(RDFS.range)) {
                Resource existingResource = resourceMap.get(referenceName);

                if (existingResource != null) {
                    subject.addProperty(property, existingResource);
                    log.debug("Added mapped resource reference for domain/range: {} -> {} (from resourceMap)",
                        property.getLocalName(), existingResource.getURI());
                } else {
                    String conceptUri = uriGenerator.generateConceptURI(referenceName, null);
                    subject.addProperty(property, ontModel.createResource(conceptUri));
                    log.debug("Added generated URI reference for domain/range: {} -> {} (generated, not in resourceMap)",
                        property.getLocalName(), conceptUri);
                }
            } else {
                DataTypeConverter.addTypedProperty(subject, property, referenceName, null, ontModel);
                log.debug("Added typed literal reference: {} -> {}", property.getLocalName(), referenceName);
            }
        }
    }

    private boolean isObjectProperty(PropertyData propertyData) {
        String dataType = propertyData.getDataType();
        String domain = propertyData.getDomain();

        if (domain != null && !domain.trim().isEmpty()) {
            String trimmedDomain = domain.trim();
            if (isDatatype(trimmedDomain)) {
                return true;
            }
        }

        if (dataType != null && !dataType.trim().isEmpty()) {
            String trimmedDataType = dataType.trim();
            return isDatatype(trimmedDataType);
        }

        return false;
    }

    private boolean isDatatype(String value) {
        if (value.startsWith("xsd:") || value.startsWith(ExportConstants.Json.XSD_NS)) {
            return false;
        }

        if (CZECH_TO_XSD_MAPPING.containsKey(value)) {
            return false;
        }

        return (!CZECH_TO_XSD_MAPPING.containsKey(value)) && !value.matches("(?i)(string|boolean|integer|double|date|time|datetime|uri|literal).*");
    }

    private void addClassDataGovernanceMetadata(Resource classResource, ClassData classData) {
        addClassPublicOrNonPublicData(classResource, classData);
        handleGovernanceProperty(classResource, classData.getSharingMethod(), "sharing-method");
        handleGovernanceProperty(classResource, classData.getAcquisitionMethod(), "acquisition-method");
        handleGovernanceProperty(classResource, classData.getContentType(), "content-type");
    }

    private void addPropertyDataGovernanceMetadata(Resource propertyResource, PropertyData propertyData) {
        handleGovernanceProperty(propertyResource, propertyData.getSharingMethod(), "sharing-method");
        handleGovernanceProperty(propertyResource, propertyData.getAcquisitionMethod(), "acquisition-method");
        handleGovernanceProperty(propertyResource, propertyData.getContentType(), "content-type");
    }

    private void addPropertyPPDFData(Resource propertyResource, PropertyData propertyData) {
        addPPDFData(propertyResource, propertyData.getSharedInPPDF(), "property");
    }

    private void addPPDFData(Resource resource, String sharedInPPDF, String entityType) {
        if (sharedInPPDF != null && !sharedInPPDF.trim().isEmpty()) {
            String value = sharedInPPDF;
            Property ppdfProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + JE_PPDF);

            if (UtilityMethods.isBooleanValue(value)) {
                Boolean boolValue = UtilityMethods.normalizeCzechBoolean(value);
                DataTypeConverter.addTypedProperty(resource, ppdfProperty,
                        boolValue.toString(), null, ontModel);
                log.debug("Added normalized PPDF boolean for {}: {} -> {}", entityType, value, boolValue);
            } else {
                log.warn("Unrecognized boolean value for PPDF {} property: '{}'", entityType, value);
                DataTypeConverter.addTypedProperty(resource, ppdfProperty, value, null, ontModel);
            }
        }
    }

    private void addClassPublicOrNonPublicData(Resource classResource, ClassData classData) {
        String isPublicValue = classData.getIsPublic();
        String privacyProvision = classData.getPrivacyProvision();

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            handleClassNonPublicData(classResource, classData, privacyProvision);
            return;
        }

        if (isPublicValue != null && !isPublicValue.trim().isEmpty()) {
            handleClassPublicData(classResource, classData, isPublicValue, privacyProvision);
        }
    }

    private void handleClassNonPublicData(Resource classResource, ClassData classData, String privacyProvision) {
        Property dataClassificationProp = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + "data-classification");
        classResource.addProperty(dataClassificationProp, "non-public");
        classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + NEVEREJNY_UDAJ));
        log.debug("Added non-public data annotation and RDF type for class: {}", classData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddClassPrivacyProvision(classResource, classData, privacyProvision);
        }
    }

    private void handleClassPublicData(Resource classResource, ClassData classData, String isPublicValue, String privacyProvision) {
        if (UtilityMethods.isBooleanValue(isPublicValue)) {
            Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

            if (Boolean.TRUE.equals(isPublic)) {
                if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                    log.warn("Class '{}' marked as public but has privacy provision '{}' - treating as non-public",
                            classData.getName(), privacyProvision);
                    handleClassNonPublicData(classResource, classData, privacyProvision);
                } else {
                    Property dataClassificationProp = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + "data-classification");
                    classResource.addProperty(dataClassificationProp, "public");
                    classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VEREJNY_UDAJ));
                    log.debug("Added public data annotation and RDF type for class: {}", classData.getName());
                }
            } else {
                handleClassNonPublicData(classResource, classData, privacyProvision);
            }
        } else {
            log.warn("Unrecognized boolean value for public class property: '{}' for class '{}'",
                    isPublicValue, classData.getName());
        }
    }

    private void validateAndAddClassPrivacyProvision(Resource classResource, ClassData classData, String provision) {
        String trimmedProvision = provision.trim();

        if (UtilityMethods.containsEliPattern(trimmedProvision)) {
            String eliPart = UtilityMethods.extractEliPart(trimmedProvision);
            if (eliPart != null) {
                String transformedProvision = "https://opendata.eselpoint.cz/esel-esb/" + eliPart;
                Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + USTANOVENI_NEVEREJNOST);

                if (DataTypeConverter.isUri(transformedProvision)) {
                    classResource.addProperty(provisionProperty, ontModel.createResource(transformedProvision));
                    log.debug("Added privacy provision as URI for class '{}': {} -> {}",
                            classData.getName(), trimmedProvision, transformedProvision);
                } else {
                    DataTypeConverter.addTypedProperty(classResource, provisionProperty, transformedProvision, null, ontModel);
                    log.debug("Added privacy provision as literal for class '{}': {} -> {}",
                            classData.getName(), trimmedProvision, transformedProvision);
                }
            } else {
                log.warn("Failed to extract ELI part from privacy provision for class '{}': '{}'",
                        classData.getName(), trimmedProvision);
            }
        } else {
            log.debug("Privacy provision does not contain ELI pattern for class '{}': '{}' - skipping",
                    classData.getName(), trimmedProvision);
        }
    }

    private void addRelationshipPPDFData(Resource relationshipResource, RelationshipData relationshipData) {
        addPPDFData(relationshipResource, relationshipData.getSharedInPPDF(), "relationship");
    }

    private void addRelationshipAgenda(Resource relationshipResource, RelationshipData relationshipData) {
        addAgenda(relationshipResource, relationshipData.getAgendaCode(), "relationship", relationshipData.getName());
    }

    private void addRelationshipAgendaInformationSystem(Resource relationshipResource, RelationshipData relationshipData) {
        addAgendaInformationSystem(relationshipResource, relationshipData.getAgendaSystemCode(), "relationship", relationshipData.getName());
    }

    private void addRelationshipDataGovernanceMetadata(Resource relationshipResource, RelationshipData relationshipData) {
        handleGovernanceProperty(relationshipResource, relationshipData.getSharingMethod(), "sharing-method");
        handleGovernanceProperty(relationshipResource, relationshipData.getAcquisitionMethod(), "acquisition-method");
        handleGovernanceProperty(relationshipResource, relationshipData.getContentType(), "content-type");
    }
}