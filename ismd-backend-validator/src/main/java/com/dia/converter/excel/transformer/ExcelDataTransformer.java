package com.dia.converter.excel.transformer;

import com.dia.converter.excel.data.*;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dia.constants.ArchiOntologyConstants.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;

/**
 * OntologyDataTransformer - Transforms Excel-parsed data into OFN ontology models
 * <p>
 * This transformer bridges the gap between the Excel reader output (OntologyData)
 * and the format expected by the existing JSON and Turtle exporters.
 * <p>
 * Key responsibilities:
 * 1. Transform OntologyData into populated OntModel
 * 2. Use DataTypeConverter for intelligent type detection
 * 3. Use UtilityMethods for URI generation and validation
 * 4. Create the resourceMap expected by exporters
 * 5. Preserve all Excel metadata in appropriate RDF properties
 */
@Component
@Slf4j
@Getter
public class ExcelDataTransformer {

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
    private Boolean removeELI = false;

    public ExcelDataTransformer() {
        this.baseModel = new OFNBaseModel();
        this.ontModel = baseModel.getOntModel();
        this.resourceMap = new HashMap<>();
        this.uriGenerator = new URIGenerator();
    }

    /**
     * Main transformation method
     * @param ontologyData Parsed Excel data
     * @return TransformationResult containing everything needed for export
     */
    public TransformationResult transform(OntologyData ontologyData, boolean removeInvalidSources) throws ConversionException {
        try {
            log.info("Starting ontology data transformation...");

            if (ontologyData == null || ontologyData.getVocabularyMetadata() == null) {
                throw new ConversionException("Invalid ontology data");
            }
            this.removeELI = removeInvalidSources;

            String effectiveNamespace = determineEffectiveNamespace(ontologyData.getVocabularyMetadata());
            uriGenerator.setEffectiveNamespace(effectiveNamespace);

            log.debug("Using effective namespace: {}", effectiveNamespace);
            log.debug("Model name: {}", ontologyData.getVocabularyMetadata().getName());

            initializeCleanTypeClasses();
            createOntologyResource(ontologyData.getVocabularyMetadata(), effectiveNamespace);
            transformClasses(ontologyData.getClasses());
            transformProperties(ontologyData.getProperties());
            transformRelationships(ontologyData.getRelationships());

            establishHierarchies(ontologyData);

            Map<String, String> modelProperties = createModelProperties(ontologyData.getVocabularyMetadata());

            log.info("Transformation completed successfully. Created {} classes, {} properties, {} relationships",
                    classResources.size(), propertyResources.size(), relationshipResources.size());

            return new TransformationResult(
                    ontModel,
                    new HashMap<>(resourceMap), // defensive copy
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
            log.debug("Creating JSON exporter: requestId={}", requestId);
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
        } catch (JsonExportException e) {
            log.error("JSON export error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during JSON export: requestId={}", requestId, e);
            throw new JsonExportException("Neočekávaná chyba při exportu do JSON.", e);
        }
    }

    public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}, modelName={}", requestId, modelName);

        try {
            log.debug("Creating Turtle exporter: requestId={}", requestId);
            TurtleExporter exporter = new TurtleExporter(
                    transformationResult.getOntModel(),
                    transformationResult.getResourceMap(),
                    transformationResult.getModelName(),
                    transformationResult.getModelProperties()
            );
            String result = exporter.exportToTurtle();
            log.info("Turtle export completed: requestId={}, outputSize={}", requestId, result.length());
            return result;
        } catch (TurtleExportException e) {
            log.error("Turtle export error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Turtle export: requestId={}", requestId, e);
            throw new TurtleExportException("Neočekávaná chyba při exportu do Turtle.", e);
        }
    }

    /**
     * Creates the ontology resource with proper IRI based on vocabulary metadata
     */
    private void createOntologyResource(VocabularyMetadata metadata, String effectiveNamespace) {
        String ontologyIRI = assembleOntologyIRI(metadata.getName(), effectiveNamespace);

        log.debug("Creating ontology resource with IRI: {}", ontologyIRI);

        ontModel.createOntology(ontologyIRI);

        Resource ontologyResource = ontModel.getResource(ontologyIRI);
        if (ontologyResource != null) {
            ontologyResource.addProperty(RDF.type, ontModel.getResource("http://www.w3.org/2002/07/owl#Ontology"));
            ontologyResource.addProperty(RDF.type, SKOS.ConceptScheme);

            if (metadata.getName() != null && !metadata.getName().trim().isEmpty()) {
                ontologyResource.addProperty(SKOS.prefLabel, metadata.getName(), "cs");
            }

            if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
                Property descProperty = ontModel.createProperty("http://purl.org/dc/terms/description");
                DataTypeConverter.addTypedProperty(ontologyResource, descProperty, metadata.getDescription(), "cs", ontModel);
            }

            resourceMap.put("ontology", ontologyResource);

            log.debug("Ontology resource created successfully: {}", ontologyIRI);
        }
    }

    /**
     * Initializes clean type classes with hyphenated URIs
     * This prevents the creation of type classes with spaces in URIs
     */
    private void initializeCleanTypeClasses() {
        String namespace = uriGenerator.getEffectiveNamespace();

        ontModel.createResource(namespace + TYP_POJEM);
        ontModel.createResource(namespace + TYP_TRIDA);
        ontModel.createResource(namespace + TYP_VZTAH);
        ontModel.createResource(namespace + TYP_VLASTNOST);
        ontModel.createResource(namespace + TYP_TSP);
        ontModel.createResource(namespace + TYP_TOP);
        ontModel.createResource(namespace + TYP_VEREJNY_UDAJ);
        ontModel.createResource(namespace + TYP_NEVEREJNY_UDAJ);

        log.debug("Initialized clean type classes with hyphenated URIs");
    }

    /**
     * Assembles the complete ontology IRI from name and namespace (like ArchiConverter)
     */
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

    /**
     * Determines the effective namespace from vocabulary metadata
     */
    private String determineEffectiveNamespace(VocabularyMetadata metadata) {
        String namespace = metadata.getNamespace();

        if (namespace != null && !namespace.trim().isEmpty() && UtilityMethods.isValidUrl(namespace)) {
            return UtilityMethods.ensureNamespaceEndsWithDelimiter(namespace);
        }

        return NS;
    }

    private void addSchemeRelationship(Resource resource) {
        Resource ontologyResource = resourceMap.get("ontology");
        if (ontologyResource != null && resource.hasProperty(RDF.type,
                ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_POJEM))) {
            resource.addProperty(SKOS.inScheme, ontologyResource);
            log.debug("Added inScheme relationship for resource: {}", resource.getURI());
        }
    }

    /**
     * Creates model properties map
     */
    private Map<String, String> createModelProperties(VocabularyMetadata metadata) {
        Map<String, String> properties = new HashMap<>();

        if (metadata.getName() != null) {
            properties.put(LABEL_NAZEV, metadata.getName());
        }

        if (metadata.getDescription() != null) {
            properties.put(LABEL_POPIS, metadata.getDescription());
        }

        if (metadata.getNamespace() != null) {
            properties.put(LABEL_ALKD, metadata.getNamespace());
        }

        return properties;
    }

    /**
     * Transforms Excel class data into ontology classes
     */
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

    /**
     * Creates an individual class resource from ClassData
     */
    private Resource createClassResource(ClassData classData) {
        String classURI = uriGenerator.generateClassURI(classData.getName(), classData.getIdentifier());
        Resource classResource = ontModel.createResource(classURI);

        classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_POJEM));

        addSpecificClassType(classResource, classData);

        addResourceMetadata(classResource, classData.getName(), classData.getDescription(),
                classData.getDefinition(), classData.getSource());

        addClassSpecificMetadata(classResource, classData);

        addSchemeRelationship(classResource);

        return classResource;
    }

    /**
     * Adds specific type classification based on Excel data
     */
    private void addSpecificClassType(Resource classResource, ClassData classData) {
        String excelType = classData.getType();

        if ("Subjekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_TSP));
        } else if ("Objekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_TOP));
        }

        classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_TRIDA));
    }

    /**
     * Transforms Excel property data into ontology properties
     */
    private void transformProperties(List<PropertyData> properties) {
        log.debug("Transforming {} properties", properties.size());

        for (PropertyData propertyData : properties) {
            if (!propertyData.hasValidData()) {
                log.warn("Skipping invalid property: {}", propertyData.getName());
                continue;
            }

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

    /**
     * Creates an individual property resource from PropertyData
     */
    private Resource createPropertyResource(PropertyData propertyData) {
        String propertyURI = uriGenerator.generatePropertyURI(propertyData.getName(), propertyData.getIdentifier());
        Resource propertyResource = ontModel.createResource(propertyURI);

        propertyResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_POJEM));
        propertyResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_VLASTNOST));

        addResourceMetadata(propertyResource, propertyData.getName(), propertyData.getDescription(),
                propertyData.getDefinition(), propertyData.getSource());

        addDomainRelationship(propertyResource, propertyData);

        addRangeInformation(propertyResource, propertyData);

        addDataGovernanceMetadata(propertyResource, propertyData);

        addMultilingualLabels(propertyResource, propertyData);

        addSchemeRelationship(propertyResource);

        return propertyResource;
    }

    /**
     * Transforms Excel relationship data into ontology relationships
     */
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

    /**
     * Creates an individual relationship resource from RelationshipData
     */
    private Resource createRelationshipResource(RelationshipData relationshipData) {
        String relationshipURI = uriGenerator.generateRelationshipURI(relationshipData.getName(),
                relationshipData.getIdentifier());
        Resource relationshipResource = ontModel.createResource(relationshipURI);

        relationshipResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_POJEM));
        relationshipResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_VZTAH));

        addResourceMetadata(relationshipResource, relationshipData.getName(), relationshipData.getDescription(),
                relationshipData.getDefinition(), relationshipData.getSource());

        addDomainRangeRelationships(relationshipResource, relationshipData);

        addMultilingualLabels(relationshipResource, relationshipData);

        addSchemeRelationship(relationshipResource);

        return relationshipResource;
    }

    /**
     * Adds core metadata (name, description, definition, source) to any resource
     * Uses DataTypeConverter for intelligent typing
     */
    private void addResourceMetadata(Resource resource, String name, String description,
                                     String definition, String source) {
        if (name != null && !name.trim().isEmpty()) {
            DataTypeConverter.addTypedProperty(resource, RDFS.label, name, "cs", ontModel);
        }

        if (description != null && !description.trim().isEmpty()) {
            Property descProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_POPIS);
            DataTypeConverter.addTypedProperty(resource, descProperty, description, "cs", ontModel);
        }

        if (definition != null && !definition.trim().isEmpty()) {
            Property defProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_DEF);
            DataTypeConverter.addTypedProperty(resource, defProperty, definition, "cs", ontModel);
        }

        if (source != null && !source.trim().isEmpty()) {
            addSourceReferences(resource, source);
        }
    }

    /**
     * Handles multiple source URLs (semicolon-separated) with ELI transformation
     * Following ArchiConverter pattern
     */
    private void addSourceReferences(Resource resource, String sourceUrls) {
        if (sourceUrls.contains(";")) {
            addMultipleSourceUrls(resource, sourceUrls);
        } else {
            addSingleSourceUrl(resource, sourceUrls);
        }
    }

    /**
     * Processes multiple semicolon-separated source URLs
     */
    private void addMultipleSourceUrls(Resource resource, String sourceUrlString) {
        String[] urls = sourceUrlString.split(";");
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                addSingleSourceUrl(resource, url.trim());
            }
        }
    }

    /**
     * Adds a single source URL with ELI transformation support
     */
    private void addSingleSourceUrl(Resource resource, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String transformedUrl = UtilityMethods.transformEliUrl(url, getRemoveELI());
        Property sourceProp = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_ZDROJ);
        if (DataTypeConverter.isUri(transformedUrl)) {
            resource.addProperty(sourceProp, ontModel.createResource(transformedUrl));
        } else {
            DataTypeConverter.addTypedProperty(resource, sourceProp, transformedUrl, null, ontModel);
        }
    }

    /**
     * Adds class-specific metadata (alternative names, equivalent concepts, etc.)
     */
    private void addClassSpecificMetadata(Resource classResource, ClassData classData) {
        if (classData.getAlternativeName() != null && !classData.getAlternativeName().trim().isEmpty()) {
            addAlternativeNames(classResource, classData.getAlternativeName());
        }

        if (classData.getEquivalentConcept() != null && !classData.getEquivalentConcept().trim().isEmpty() &&
                DataTypeConverter.isUri(classData.getEquivalentConcept())) {
            classResource.addProperty(ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch"),
                    ontModel.createResource(classData.getEquivalentConcept()));
        }


        if (classData.getAgendaCode() != null && !classData.getAgendaCode().trim().isEmpty()) {
            Property agendaProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_AGENDA);
            DataTypeConverter.addTypedProperty(classResource, agendaProperty,
                    classData.getAgendaCode(), null, ontModel);
        }

        if (classData.getAgendaSystemCode() != null && !classData.getAgendaSystemCode().trim().isEmpty()) {
            Property aisProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_AIS);
            DataTypeConverter.addTypedProperty(classResource, aisProperty,
                    classData.getAgendaSystemCode(), null, ontModel);
        }

        addMultilingualLabels(classResource, classData);
    }

    /**
     * Handles multiple alternative names (semicolon-separated)
     * Following ArchiConverter pattern
     */
    private void addAlternativeNames(Resource resource, String altNamesValue) {
        if (altNamesValue == null || altNamesValue.isEmpty()) {
            return;
        }

        Property altNameProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_AN);

        if (!altNamesValue.contains(";")) {
            DataTypeConverter.addTypedProperty(resource, altNameProperty, altNamesValue, "cs", ontModel);
            log.debug("Added single alternative name: {}", altNamesValue);
            return;
        }

        Arrays.stream(altNamesValue.split(";"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(name -> {
                    DataTypeConverter.addTypedProperty(resource, altNameProperty, name, "cs", ontModel);
                    log.debug("Added alternative name: {}", name);
                });
    }

    /**
     * Adds multilingual labels for resources
     * Enhanced support for multiple languages beyond Czech
     */
    private void addMultilingualLabels(Resource resource, ClassData classData) {
        // Add English label if available (assuming ClassData might have it)
        // This would require extending ClassData to include multilingual fields
        // For now, this is a placeholder for the pattern

        // Example pattern for when multilingual data is available:
        // if (classData.getEnglishName() != null && !classData.getEnglishName().trim().isEmpty()) {
        //     resource.addProperty(RDFS.label, classData.getEnglishName(), "en");
        //     log.debug("Added English label: {}", classData.getEnglishName());
        // }

        log.debug("Multilingual label processing completed for resource: {}", resource.getURI());
    }

    /**
     * Enhanced property-specific multilingual support
     */
    private void addMultilingualLabels(Resource resource, PropertyData propertyData) {
        // Placeholder for property multilingual labels
        // Similar pattern as above for ClassData
        log.debug("Multilingual label processing completed for property: {}", resource.getURI());
    }

    /**
     * Enhanced relationship-specific multilingual support
     */
    private void addMultilingualLabels(Resource resource, RelationshipData relationshipData) {
        // Placeholder for relationship multilingual labels
        // Similar pattern as above
        log.debug("Multilingual label processing completed for relationship: {}", resource.getURI());
    }

    /**
     * Adds domain relationship for properties
     */
    private void addDomainRelationship(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getDomain() != null && !propertyData.getDomain().trim().isEmpty()) {
            Resource domainResource = classResources.get(propertyData.getDomain());
            if (domainResource != null) {
                Property domainProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_DEF_O);
                propertyResource.addProperty(domainProperty, domainResource);
            } else {
                log.warn("Domain class '{}' not found for property '{}'",
                        propertyData.getDomain(), propertyData.getName());
            }
        }
    }

    /**
     * Adds range/data type information using DataTypeConverter intelligence
     */
    private void addRangeInformation(Resource propertyResource, PropertyData propertyData) {
        String dataType = propertyData.getDataType();

        if (dataType != null && !dataType.trim().isEmpty()) {
            String xsdType = UtilityMethods.mapDataTypeToXSD(dataType);
            Property rangeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_OBOR_HODNOT);
            propertyResource.addProperty(rangeProperty, ontModel.createResource(xsdType));
        }
    }

    /**
     * Adds Czech data governance metadata
     */
    private void addDataGovernanceMetadata(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getSharedInPPDF() != null && !propertyData.getSharedInPPDF().trim().isEmpty()) {
            Property ppdfProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_JE_PPDF);
            DataTypeConverter.addTypedProperty(propertyResource, ppdfProperty,
                    propertyData.getSharedInPPDF(), null, ontModel);
        }

        if (propertyData.getIsPublic() != null && !propertyData.getIsPublic().trim().isEmpty()) {
            if (DataTypeConverter.isBooleanValue(propertyData.getIsPublic()) &&
                    !"ne".equalsIgnoreCase(propertyData.getIsPublic()) &&
                    !"false".equalsIgnoreCase(propertyData.getIsPublic())) {

                propertyResource.addProperty(RDF.type,
                        ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_VEREJNY_UDAJ));
            } else {
                propertyResource.addProperty(RDF.type,
                        ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_NEVEREJNY_UDAJ));

                if (propertyData.getPrivacyProvision() != null && !propertyData.getPrivacyProvision().trim().isEmpty()) {
                    Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_UDN);
                    DataTypeConverter.addTypedProperty(propertyResource, provisionProperty,
                            propertyData.getPrivacyProvision(), null, ontModel);
                }
            }
        }
    }

    /**
     * Adds domain and range relationships for object properties
     */
    private void addDomainRangeRelationships(Resource relationshipResource, RelationshipData relationshipData) {
        if (relationshipData.getDomain() != null && !relationshipData.getDomain().trim().isEmpty()) {
            Resource domainResource = classResources.get(relationshipData.getDomain());
            if (domainResource != null) {
                Property domainProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_DEF_O);
                relationshipResource.addProperty(domainProperty, domainResource);
            }
        }

        if (relationshipData.getRange() != null && !relationshipData.getRange().trim().isEmpty()) {
            Resource rangeResource = findOrCreateRangeResource(relationshipData.getRange());
            if (rangeResource != null) {
                Property rangeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_OBOR_HODNOT);
                relationshipResource.addProperty(rangeProperty, rangeResource);
            }
        }
    }

    /**
     * Finds existing range resource or creates external reference
     */
    private Resource findOrCreateRangeResource(String rangeName) {
        Resource existingClass = classResources.get(rangeName);
        if (existingClass != null) {
            return existingClass;
        }

        if (DataTypeConverter.isUri(rangeName)) {
            return ontModel.createResource(rangeName);
        }

        log.warn("Range '{}' not found as local class and not a valid URI", rangeName);
        return null;
    }

    /**
     * Establishes hierarchical relationships between classes
     */
    private void establishHierarchies(OntologyData ontologyData) {
        log.debug("Establishing class hierarchies");

        for (ClassData classData : ontologyData.getClasses()) {
            if (classData.getSuperClass() != null && !classData.getSuperClass().trim().isEmpty()) {
                Resource childResource = classResources.get(classData.getName());
                Resource parentResource = classResources.get(classData.getSuperClass());

                if (childResource != null && parentResource != null) {
                    Property hierarchyProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_NT);
                    childResource.addProperty(hierarchyProperty, parentResource);

                    log.debug("Established hierarchy: {} -> {}", classData.getName(), classData.getSuperClass());
                } else {
                    log.warn("Could not establish hierarchy for {} -> {}",
                            classData.getName(), classData.getSuperClass());
                }
            }
        }
    }
}
