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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dia.constants.ArchiOntologyConstants.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;

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

            establishHierarchies(ontologyData);

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
        return NS;
    }

    private void addSchemeRelationship(Resource resource) {
        Resource ontologyResource = resourceMap.get("ontology");
        if (ontologyResource != null && resource.hasProperty(RDF.type,
                ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_POJEM))) {
            resource.addProperty(SKOS.inScheme, ontologyResource);
        }
    }

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

    private void addSpecificClassType(Resource classResource, ClassData classData) {
        String excelType = classData.getType();
        if ("Subjekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_TSP));
        } else if ("Objekt práva".equals(excelType)) {
            classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_TOP));
        }
        classResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_TRIDA));
    }

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
        addSchemeRelationship(propertyResource);
        return propertyResource;
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

        relationshipResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_POJEM));
        relationshipResource.addProperty(RDF.type, ontModel.getResource(uriGenerator.getEffectiveNamespace() + TYP_VZTAH));

        addResourceMetadata(relationshipResource, relationshipData.getName(), relationshipData.getDescription(),
                relationshipData.getDefinition(), relationshipData.getSource());
        addDomainRangeRelationships(relationshipResource, relationshipData);
        addSchemeRelationship(relationshipResource);
        return relationshipResource;
    }

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

    private void addSourceReferences(Resource resource, String sourceUrls) {
        if (sourceUrls.contains(";")) {
            addMultipleSourceUrls(resource, sourceUrls);
        } else {
            addSingleSourceUrl(resource, sourceUrls);
        }
    }

    private void addMultipleSourceUrls(Resource resource, String sourceUrlString) {
        String[] urls = sourceUrlString.split(";");
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                addSingleSourceUrl(resource, url.trim());
            }
        }
    }

    private void addSingleSourceUrl(Resource resource, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String transformedUrl = UtilityMethods.transformEliUrl(url, removeELI);
        Property sourceProp = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_ZDROJ);

        if (DataTypeConverter.isUri(transformedUrl)) {
            resource.addProperty(sourceProp, ontModel.createResource(transformedUrl));
            log.debug("Added source URL as resource: {}", transformedUrl);
        } else {
            DataTypeConverter.addTypedProperty(resource, sourceProp, transformedUrl, null, ontModel);
            log.debug("Added source URL as typed literal: {}", transformedUrl);
        }
    }

    private void addClassSpecificMetadata(Resource classResource, ClassData classData) {
        if (classData.getAlternativeName() != null && !classData.getAlternativeName().trim().isEmpty()) {
            addAlternativeNames(classResource, classData.getAlternativeName());
        }

        if (classData.getEquivalentConcept() != null && !classData.getEquivalentConcept().trim().isEmpty()) {
            Property exactMatchProperty = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch");
            String equivalentConcept = classData.getEquivalentConcept();

            if (DataTypeConverter.isUri(equivalentConcept)) {
                classResource.addProperty(exactMatchProperty, ontModel.createResource(equivalentConcept));
                log.debug("Added equivalent concept as URI: {}", equivalentConcept);
            } else {
                DataTypeConverter.addTypedProperty(classResource, exactMatchProperty, equivalentConcept, null, ontModel);
                log.debug("Added equivalent concept as typed literal: {}", equivalentConcept);
            }
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
    }

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

    private void addDomainRelationship(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getDomain() != null && !propertyData.getDomain().trim().isEmpty()) {
            Property domainProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_DEF_O);
            addResourceReference(propertyResource, domainProperty, propertyData.getDomain(), classResources);
        }
    }

    private void addRangeInformation(Resource propertyResource, PropertyData propertyData) {
        String dataType = propertyData.getDataType();

        if (dataType != null && !dataType.trim().isEmpty()) {
            Property rangeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_OBOR_HODNOT);

            if (dataType.startsWith("xsd:")) {
                String xsdType = XSD + dataType.substring(4);
                propertyResource.addProperty(rangeProperty, ontModel.createResource(xsdType));
                log.debug("Added XSD range type: {}", xsdType);
            } else if (DataTypeConverter.isUri(dataType)) {
                propertyResource.addProperty(rangeProperty, ontModel.createResource(dataType));
                log.debug("Added URI range type: {}", dataType);
            } else {
                Resource existingClass = classResources.get(dataType);
                if (existingClass != null) {
                    propertyResource.addProperty(rangeProperty, existingClass);
                    log.debug("Added local class range: {}", dataType);
                } else {
                    String xsdType = UtilityMethods.mapDataTypeToXSD(dataType);
                    propertyResource.addProperty(rangeProperty, ontModel.createResource(xsdType));
                    log.debug("Mapped data type '{}' to XSD type: {}", dataType, xsdType);
                }
            }
        }
    }

    private void addDataGovernanceMetadata(Resource propertyResource, PropertyData propertyData) {
        addPPDFData(propertyResource, propertyData);
        addPublicOrNonPublicData(propertyResource, propertyData);
        addGovernanceProperty(propertyResource, propertyData.getSharingMethod(), LABEL_ZPUSOB_SDILENI);
        addGovernanceProperty(propertyResource, propertyData.getAcquisitionMethod(), LABEL_ZPUSOB_ZISKANI);
        addGovernanceProperty(propertyResource, propertyData.getContentType(), LABEL_TYP_OBSAHU);
    }

    private void addPPDFData(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getSharedInPPDF() != null && !propertyData.getSharedInPPDF().trim().isEmpty()) {
            String value = propertyData.getSharedInPPDF();
            Property ppdfProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_JE_PPDF);

            if (DataTypeConverter.isBooleanValue(value)) {
                boolean boolValue = "true".equalsIgnoreCase(value) ||
                        "ano".equalsIgnoreCase(value) ||
                        "yes".equalsIgnoreCase(value);
                DataTypeConverter.addTypedProperty(propertyResource, ppdfProperty,
                        boolValue ? "true" : "false", null, ontModel);
                log.debug("Added normalized PPDF boolean: {} -> {}", value, boolValue);
            } else {
                log.warn("Unrecognized boolean value for PPDF property: '{}'", value);
                DataTypeConverter.addTypedProperty(propertyResource, ppdfProperty, value, null, ontModel);
            }
        }
    }

    private void addPublicOrNonPublicData(Resource propertyResource, PropertyData propertyData) {
        if (propertyData.getIsPublic() != null && !propertyData.getIsPublic().trim().isEmpty()) {
            String value = propertyData.getIsPublic();

            if (DataTypeConverter.isBooleanValue(value)) {
                boolean isPublic = "true".equalsIgnoreCase(value) ||
                        "ano".equalsIgnoreCase(value) ||
                        "yes".equalsIgnoreCase(value);

                if (isPublic) {
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
            } else {
                log.warn("Unrecognized boolean value for public property: '{}'", value);
            }
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
            Property domainProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_DEF_O);
            addResourceReference(relationshipResource, domainProperty, relationshipData.getDomain(), classResources);
        }

        if (relationshipData.getRange() != null && !relationshipData.getRange().trim().isEmpty()) {
            Property rangeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_OBOR_HODNOT);
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

    private void establishHierarchies(OntologyData ontologyData) {
        log.debug("Establishing class hierarchies");

        for (ClassData classData : ontologyData.getClasses()) {
            if (classData.getSuperClass() != null && !classData.getSuperClass().trim().isEmpty()) {
                Resource childResource = classResources.get(classData.getName());
                if (childResource != null) {
                    Property hierarchyProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + LABEL_NT);
                    addResourceReference(childResource, hierarchyProperty, classData.getSuperClass(), classResources);
                    log.debug("Established hierarchy: {} -> {}", classData.getName(), classData.getSuperClass());
                } else {
                    log.warn("Child class resource not found for hierarchy: {}", classData.getName());
                }
            }
        }
    }
}