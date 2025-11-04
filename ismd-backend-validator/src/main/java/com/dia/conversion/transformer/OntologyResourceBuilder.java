package com.dia.conversion.transformer;

import com.dia.constants.DataTypeConstants;
import com.dia.constants.ExportConstants;
import com.dia.conversion.data.*;
import com.dia.conversion.transformer.metadata.ResourceMetadata;
import com.dia.utility.DataTypeConverter;
import com.dia.utility.URIGenerator;
import com.dia.utility.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import java.util.*;

import static com.dia.constants.ExportConstants.Common.DEFAULT_LANG;
import static com.dia.constants.VocabularyConstants.*;

/**
 * Handles creation and metadata addition for ontology resources.
 * This includes:
 * - Creating class, property, and relationship resources
 * - Adding common metadata (name, description, definition, sources)
 * - Adding specific metadata (alternative names, equivalent concepts)
 * - Processing hierarchies and super-property relationships
 * - Range and domain information
 * - Temporal metadata
 */
@Slf4j
public class OntologyResourceBuilder {

    private static final String CLASS = "class";
    private static final String RELATIONSHIP = "relationship";
    private static final Map<String, String> CZECH_TO_XSD_MAPPING = Map.of(
            "Ano či ne", DataTypeConstants.XSD_BOOLEAN,
            "Datum", DataTypeConstants.XSD_DATE,
            "Čas", DataTypeConstants.XSD_TIME,
            "Datum a čas", DataTypeConstants.XSD_DATETIME_STAMP,
            "Celé číslo", DataTypeConstants.XSD_INTEGER,
            "Desetinné číslo", DataTypeConstants.XSD_DOUBLE,
            "URI, IRI, URL", DataTypeConstants.XSD_ANY_URI,
            "Řetězec", DataTypeConstants.XSD_STRING,
            "Text", DataTypeConstants.RDFS_LITERAL
    );
    private final OntModel ontModel;
    private final URIGenerator uriGenerator;
    private final DataGovernanceProcessor governanceProcessor;
    private final ConceptFilterUtil conceptFilterUtil;
    private Map<String, Resource> allClassResourcesForHierarchies;

    public OntologyResourceBuilder(OntModel ontModel, URIGenerator uriGenerator,
                                   DataGovernanceProcessor governanceProcessor,
                                   ConceptFilterUtil conceptFilterUtil) {
        this.ontModel = ontModel;
        this.uriGenerator = uriGenerator;
        this.governanceProcessor = governanceProcessor;
        this.conceptFilterUtil = conceptFilterUtil;
    }

    public void createOntologyResourceWithTemporal(VocabularyMetadata metadata, Map<String, Resource> localResourceMap) {
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

    public void transformClasses(List<ClassData> classes, Map<String, Resource> localClassResources,
                                 Map<String, Resource> localResourceMap, Map<String, String> nameToIdentifierMap,
                                 ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Transforming {} classes", classes.size());

        Map<String, Resource> allClassResources = new HashMap<>();

        for (ClassData classData : classes) {
            if (shouldSkipClass(classData, filterStatistics)) {
                continue;
            }

            try {
                Resource classResource = processValidClass(classData, localClassResources, localResourceMap,
                        nameToIdentifierMap, filterStatistics);
                allClassResources.put(classData.getName(), classResource);
            } catch (Exception e) {
                log.error("Failed to create class: {}", classData.getName(), e);
            }
        }

        this.allClassResourcesForHierarchies = allClassResources;
    }

    private boolean shouldSkipClass(ClassData classData, ConceptFilterUtil.FilterStatistics filterStatistics) {
        if (!classData.hasValidData()) {
            log.warn("Skipping invalid class: {}", classData.getName());
            return true;
        }

        String identifier = classData.getIdentifier();
        if (conceptFilterUtil.shouldFilterConcept(identifier)) {
            log.info("FILTERED: Class '{}' with identifier '{}' matches filter regex",
                    classData.getName(), identifier);
            filterStatistics.filteredClasses++;
            return true;
        }

        return false;
    }

    private Resource processValidClass(ClassData classData, Map<String, Resource> localClassResources,
                                       Map<String, Resource> localResourceMap, Map<String, String> nameToIdentifierMap,
                                       ConceptFilterUtil.FilterStatistics filterStatistics) {
        String identifier = classData.getIdentifier();
        String classURI = uriGenerator.generateConceptURI(classData.getName(), identifier);
        log.debug("Processing class '{}' with identifier='{}' -> URI={}",
                classData.getName(), identifier, classURI);

        filterSuperClassIfNeeded(classData, nameToIdentifierMap, filterStatistics);

        Resource classResource = createClassResource(classData, localResourceMap, filterStatistics);
        registerClassResource(classData, classResource, classURI, localClassResources, localResourceMap, identifier);

        return classResource;
    }

    private void filterSuperClassIfNeeded(ClassData classData, Map<String, String> nameToIdentifierMap,
                                          ConceptFilterUtil.FilterStatistics filterStatistics) {
        if (classData.getSuperClass() != null &&
                conceptFilterUtil.isConceptFiltered(classData.getSuperClass(), nameToIdentifierMap)) {
            log.info("OMITTED: superClass '{}' for class '{}' (superClass is filtered)",
                    classData.getSuperClass(), classData.getName());
            classData.setSuperClass(null);
            filterStatistics.omittedSuperClasses++;
        }
    }

    private void registerClassResource(ClassData classData, Resource classResource, String classURI,
                                       Map<String, Resource> localClassResources,
                                       Map<String, Resource> localResourceMap, String identifier) {
        if (belongsToCurrentVocabulary(classURI)) {
            localClassResources.put(classData.getName(), classResource);
            localResourceMap.put(classData.getName(), classResource);
            log.debug("Created local class: {} -> {}", classData.getName(), classResource.getURI());
        } else {
            localResourceMap.put(classData.getName(), classResource);
            log.info("Created external concept (for relationships only): '{}' with identifier='{}' -> URI={}",
                    classData.getName(), identifier, classURI);
        }
    }

    public Resource createClassResource(ClassData classData, Map<String, Resource> localResourceMap,
                                        ConceptFilterUtil.FilterStatistics filterStatistics) {
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

    private void addClassSpecificMetadata(Resource classResource, ClassData classData,
                                          ConceptFilterUtil.FilterStatistics filterStatistics) {
        addAlternativeName(classResource, classData);
        addEquivalentConcept(classResource, classData, filterStatistics);
        addAgenda(classResource, classData);
        addAgendaInformationSystem(classResource, classData);
        governanceProcessor.addClassDataGovernanceMetadata(classResource, classData);
    }

    private void addAlternativeName(Resource classResource, ClassData classData) {
        if (classData.getAlternativeName() != null && !classData.getAlternativeName().trim().isEmpty()) {
            addAlternativeNamesAsLangString(classResource, classData.getAlternativeName());
        }
    }

    private void addEquivalentConcept(Resource classResource, ClassData classData,
                                      ConceptFilterUtil.FilterStatistics filterStatistics) {
        if (classData.getEquivalentConcept() != null && !classData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for class {}: '{}'", classData.getName(), classData.getEquivalentConcept());
            addEquivalentConcept(classResource, classData.getEquivalentConcept(), CLASS, filterStatistics);
        }
    }

    private void addAgenda(Resource classResource, ClassData classData) {
        governanceProcessor.addAgenda(classResource, classData.getAgendaCode(), CLASS, classData.getName());
    }

    private void addAgendaInformationSystem(Resource classResource, ClassData classData) {
        governanceProcessor.addAgendaInformationSystem(classResource, classData.getAgendaSystemCode(), CLASS, classData.getName());
    }

    public void transformProperties(List<PropertyData> properties, Map<String, Resource> localPropertyResources,
                                    Map<String, Resource> localResourceMap, Map<String, String> nameToIdentifierMap,
                                    ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Transforming {} properties", properties.size());
        for (PropertyData propertyData : properties) {
            boolean shouldSkip = false;

            if (conceptFilterUtil.shouldFilterConcept(propertyData.getIdentifier())) {
                log.info("FILTERED: Property '{}' with identifier '{}' matches filter regex",
                        propertyData.getName(), propertyData.getIdentifier());
                filterStatistics.filteredProperties++;
                shouldSkip = true;
            } else if (propertyData.getDomain() != null &&
                    conceptFilterUtil.isConceptFiltered(propertyData.getDomain(), nameToIdentifierMap)) {
                log.info("FILTERED: Property '{}' because its domain '{}' is filtered",
                        propertyData.getName(), propertyData.getDomain());
                filterStatistics.filteredProperties++;
                filterStatistics.omittedDomains++;
                shouldSkip = true;
            }

            if (shouldSkip) {
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

    public Resource createPropertyResource(PropertyData propertyData, Map<String, Resource> localResourceMap,
                                           ConceptFilterUtil.FilterStatistics filterStatistics) {
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

    private void addPropertySpecificMetadata(Resource propertyResource, PropertyData propertyData,
                                             Map<String, Resource> localResourceMap,
                                             ConceptFilterUtil.FilterStatistics filterStatistics) {
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

        governanceProcessor.addPropertyPPDFData(propertyResource, propertyData);
        addRangeInformation(propertyResource, propertyData);
        governanceProcessor.addPropertyDataGovernanceMetadata(propertyResource, propertyData);
    }

    private void addPropertySuperPropertyRelationship(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getSuperProperty() != null && !propertyData.getSuperProperty().trim().isEmpty()) {
            addSubPropertyOf(propertyResource, propertyData.getSuperProperty().trim());
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

        return !CZECH_TO_XSD_MAPPING.containsKey(value) && !value.matches("(?i)(string|boolean|integer|double|date|time|datetime|uri|literal).*");
    }

    public void transformRelationships(List<RelationshipData> relationships, Map<String, Resource> localRelationshipResources,
                                       Map<String, Resource> localResourceMap, Map<String, String> nameToIdentifierMap,
                                       ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Transforming {} relationships", relationships.size());
        for (RelationshipData relationshipData : relationships) {
            boolean shouldSkip = false;

            if (conceptFilterUtil.shouldFilterConcept(relationshipData.getIdentifier())) {
                log.info("FILTERED: Relationship '{}' with identifier '{}' matches filter regex",
                        relationshipData.getName(), relationshipData.getIdentifier());
                filterStatistics.filteredRelationships++;
                shouldSkip = true;
            } else if (relationshipData.getDomain() != null &&
                    conceptFilterUtil.isConceptFiltered(relationshipData.getDomain(), nameToIdentifierMap)) {
                log.info("FILTERED: Relationship '{}' because its domain '{}' is filtered",
                        relationshipData.getName(), relationshipData.getDomain());
                filterStatistics.filteredRelationships++;
                filterStatistics.omittedDomains++;
                shouldSkip = true;
            } else if (relationshipData.getRange() != null &&
                    conceptFilterUtil.isConceptFiltered(relationshipData.getRange(), nameToIdentifierMap)) {
                log.info("FILTERED: Relationship '{}' because its range '{}' is filtered",
                        relationshipData.getName(), relationshipData.getRange());
                filterStatistics.filteredRelationships++;
                filterStatistics.omittedRanges++;
                shouldSkip = true;
            }

            if (shouldSkip) {
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

    public Resource createRelationshipResource(RelationshipData relationshipData, Map<String, Resource> localResourceMap,
                                               ConceptFilterUtil.FilterStatistics filterStatistics) {
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

    private void addRelationshipSpecificMetadata(Resource relationshipResource, RelationshipData relationshipData,
                                                 ConceptFilterUtil.FilterStatistics filterStatistics) {
        log.debug("Adding relationship-specific metadata for: {}", relationshipData.getName());

        if (relationshipData.getAlternativeName() != null && !relationshipData.getAlternativeName().trim().isEmpty()) {
            log.debug("Processing alternative names for relationship {}: '{}'", relationshipData.getName(), relationshipData.getAlternativeName());
            addAlternativeNamesAsLangString(relationshipResource, relationshipData.getAlternativeName());
        } else {
            log.debug("No alternative names found for relationship: {}", relationshipData.getName());
        }

        if (relationshipData.getEquivalentConcept() != null && !relationshipData.getEquivalentConcept().trim().isEmpty()) {
            log.debug("Processing equivalent concept for relationship {}: '{}'", relationshipData.getName(), relationshipData.getEquivalentConcept());
            addEquivalentConcept(relationshipResource, relationshipData.getEquivalentConcept(), RELATIONSHIP, filterStatistics);
        }

        governanceProcessor.addRelationshipPPDFData(relationshipResource, relationshipData);
        governanceProcessor.addAgenda(relationshipResource, relationshipData.getAgendaCode(), RELATIONSHIP, relationshipData.getName());
        governanceProcessor.addAgendaInformationSystem(relationshipResource, relationshipData.getAgendaSystemCode(), RELATIONSHIP, relationshipData.getName());
        governanceProcessor.addRelationshipDataGovernanceMetadata(relationshipResource, relationshipData);
    }

    private void addRelationshipSuperPropertyRelationship(Resource relationshipResource, RelationshipData relationshipData) {
        if (relationshipData.getSuperRelation() != null && !relationshipData.getSuperRelation().trim().isEmpty()) {
            addSubPropertyOf(relationshipResource, relationshipData.getSuperRelation().trim());
        }
    }

    private void addSubPropertyOf(Resource resource, String superPropertyName) {
        String superPropertyURI = DataTypeConverter.isUri(superPropertyName)
                ? superPropertyName
                : uriGenerator.generateConceptURI(superPropertyName, null);

        resource.addProperty(RDFS.subPropertyOf, ontModel.createResource(superPropertyURI));
        log.debug("Added subPropertyOf relationship: {} -> {}", resource.getURI(), superPropertyURI);
    }

    private void addDomainRangeRelationships(Resource relationshipResource, RelationshipData relationshipData,
                                             Map<String, Resource> localResourceMap) {
        if (relationshipData.getDomain() != null && !relationshipData.getDomain().trim().isEmpty()) {
            addResourceReference(relationshipResource, RDFS.domain, relationshipData.getDomain(), localResourceMap);
        }

        if (relationshipData.getRange() != null && !relationshipData.getRange().trim().isEmpty()) {
            addResourceReference(relationshipResource, RDFS.range, relationshipData.getRange(), localResourceMap);
        }
    }

    public void transformHierarchies(List<HierarchyData> hierarchies, Map<String, Resource> localClassResources,
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
            boolean shouldSkip = false;

            if (!hierarchyData.hasValidData()) {
                log.warn("Skipping invalid hierarchy: {}", hierarchyData);
                skippedHierarchies++;
                shouldSkip = true;
            } else if (conceptFilterUtil.isConceptFiltered(hierarchyData.getSubClass(), nameToIdentifierMap)) {
                log.info("FILTERED: Hierarchy '{}' IS-A '{}' because subClass is filtered",
                        hierarchyData.getSubClass(), hierarchyData.getSuperClass());
                filterStats.filteredHierarchies++;
                skippedHierarchies++;
                shouldSkip = true;
            } else if (conceptFilterUtil.isConceptFiltered(hierarchyData.getSuperClass(), nameToIdentifierMap)) {
                log.info("FILTERED: Hierarchy '{}' IS-A '{}' because superClass is filtered",
                        hierarchyData.getSubClass(), hierarchyData.getSuperClass());
                filterStats.filteredHierarchies++;
                skippedHierarchies++;
                shouldSkip = true;
            }

            if (shouldSkip) {
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
        addTypedProperties(resource, metadata);
        addSourceMetadata(resource, metadata);
    }

    private void addTypedProperties(Resource resource, ResourceMetadata metadata) {
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
    }

    private void addSourceMetadata(Resource resource, ResourceMetadata metadata) {
        if (metadata.source() != null && !metadata.source().trim().isEmpty()) {
            governanceProcessor.processSourceField(resource, metadata.source(), true);
        }

        if (metadata.relatedSource() != null && !metadata.relatedSource().trim().isEmpty()) {
            governanceProcessor.processSourceField(resource, metadata.relatedSource(), false);
        }
    }

    private void addEquivalentConcept(Resource resource, String equivalentConcept, String entityType,
                                      ConceptFilterUtil.FilterStatistics filterStatistics) {
        String[] concepts = equivalentConcept.contains(";")
            ? equivalentConcept.split(";")
            : new String[]{equivalentConcept};

        Property exactMatchProperty = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch");

        for (String concept : concepts) {
            String trimmedConcept = concept.trim();

            if (trimmedConcept.isEmpty()) {
                continue;
            }

            if (!UtilityMethods.isValidIRI(trimmedConcept)) {
                log.warn("Skipping invalid equivalent concept for {} '{}': '{}' is not a valid IRI",
                        entityType, resource.getLocalName(), trimmedConcept);
                continue;
            }

            if (conceptFilterUtil.shouldFilterConcept(trimmedConcept)) {
                log.info("OMITTED: equivalentConcept '{}' for {} '{}' (equivalentConcept is filtered)",
                        trimmedConcept, entityType, resource.getLocalName());
                filterStatistics.omittedEquivalentConcepts++;
                continue;
            }

            resource.addProperty(exactMatchProperty, ontModel.createResource(trimmedConcept));
            log.debug("Added valid equivalent concept IRI for {} '{}': {}",
                    entityType, resource.getLocalName(), trimmedConcept);
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

    private void addSchemeRelationship(Resource resource, Map<String, Resource> localResourceMap) {
        Resource ontologyResource = localResourceMap.get("ontology");
        if (ontologyResource != null && resource.hasProperty(RDF.type,
                ontModel.getResource(OFN_NAMESPACE + POJEM))) {
            resource.addProperty(SKOS.inScheme, ontologyResource);
        }
    }

    private void addResourceReference(Resource subject, Property property, String referenceName,
                                      Map<String, Resource> resourceMap) {
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
            } else {
                log.warn("Invalid XSD type '{}' - falling back to rdfs:Literal", trimmedDataType);
                propertyResource.addProperty(rangeProperty, ontModel.createResource(DataTypeConstants.RDFS_LITERAL));
            }
            return true;
        }
        return false;
    }

    private boolean checkIfFullXsdUri(String trimmedDataType, Property rangeProperty, Resource propertyResource) {
        if (trimmedDataType.startsWith(ExportConstants.Json.XSD_NS)) {
            String localName = trimmedDataType.substring(ExportConstants.Json.XSD_NS.length());
            if (DataTypeConverter.isValidXSDType(localName)) {
                propertyResource.addProperty(rangeProperty, ontModel.createResource(trimmedDataType));
                log.debug("Added valid full XSD URI range type: {}", trimmedDataType);
            } else {
                log.warn("Invalid XSD URI '{}' - falling back to rdfs:Literal", trimmedDataType);
                propertyResource.addProperty(rangeProperty, ontModel.createResource(DataTypeConstants.RDFS_LITERAL));
            }
            return true;
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

    private boolean belongsToCurrentVocabulary(String conceptURI) {
        if (conceptURI == null || uriGenerator.getEffectiveNamespace() == null) {
            return false;
        }

        boolean belongs = conceptURI.startsWith(uriGenerator.getEffectiveNamespace());
        log.debug("Namespace check for {}: belongs to current vocabulary = {} (effective namespace: {})",
                conceptURI, belongs, uriGenerator.getEffectiveNamespace());
        return belongs;
    }
}