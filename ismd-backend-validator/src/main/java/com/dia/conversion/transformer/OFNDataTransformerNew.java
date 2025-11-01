package com.dia.conversion.transformer;

import com.dia.conversion.data.*;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import com.dia.exporter.JsonExporter;
import com.dia.exporter.TurtleExporter;
import com.dia.models.OFNBaseModel;
import com.dia.utility.URIGenerator;
import com.dia.utility.UtilityMethods;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.dia.constants.VocabularyConstants.*;
import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;

/**
 * Main orchestrator for OFN ontology transformation.
 * <p>
 * This class coordinates the transformation workflow:
 * 1. Analyzes input data to determine required base classes and properties
 * 2. Creates dynamic OFN base model with required components
 * 3. Delegates resource creation to OntologyResourceBuilder
 * 4. Delegates governance processing to DataGovernanceProcessor
 * 5. Exports results to JSON or Turtle formats
 * <p>
 * Responsibilities:
 * - High-level workflow orchestration
 * - Model initialization and namespace setup
 * - Base class and property requirement analysis
 * - Export delegation
 * - Filtering coordination
 */
@Component
@Slf4j
@Getter
public class OFNDataTransformerNew {

    private OntModel ontModel;
    private final URIGenerator uriGenerator;
    private final ConceptFilterUtil conceptFilterUtil;

    public OFNDataTransformerNew(ConceptFilterUtil conceptFilterUtil) {
        this.uriGenerator = new URIGenerator();
        this.conceptFilterUtil = conceptFilterUtil;
    }

    /**
     * Main transformation method - orchestrates the entire conversion process.
     *
     * @param ontologyData Input data containing classes, properties, relationships, hierarchies
     * @return TransformationResult containing the transformed OntModel and metadata
     * @throws ConversionException if transformation fails
     */
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

            DataGovernanceProcessor governanceProcessor = new DataGovernanceProcessor(ontModel, uriGenerator);
            OntologyResourceBuilder resourceBuilder = new OntologyResourceBuilder(
                    ontModel, uriGenerator, governanceProcessor, conceptFilterUtil);

            Map<String, Resource> localResourceMap = new HashMap<>();
            Map<String, Resource> localClassResources = new HashMap<>();
            Map<String, Resource> localPropertyResources = new HashMap<>();
            Map<String, Resource> localRelationshipResources = new HashMap<>();

            resourceBuilder.createOntologyResourceWithTemporal(ontologyData.getVocabularyMetadata(), localResourceMap);

            resourceBuilder.transformClasses(ontologyData.getClasses(), localClassResources, localResourceMap,
                    nameToIdentifierMap, filterStatistics);
            resourceBuilder.transformProperties(ontologyData.getProperties(), localPropertyResources, localResourceMap,
                    nameToIdentifierMap, filterStatistics);
            resourceBuilder.transformRelationships(ontologyData.getRelationships(), localRelationshipResources,
                    localResourceMap, nameToIdentifierMap, filterStatistics);
            resourceBuilder.transformHierarchies(ontologyData.getHierarchies(), localClassResources,
                    localPropertyResources, localResourceMap, nameToIdentifierMap, filterStatistics);

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

    /**
     * Exports the transformation result to JSON format.
     */
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

    /**
     * Exports the transformation result to Turtle (TTL) format.
     */
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

    /**
     * Analyzes ontology data to determine which base classes are required.
     * This enables dynamic model creation with only necessary components.
     */
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

    /**
     * Analyzes ontology data to determine which properties are required.
     * Delegates to RequiredPropertiesAnalyzer for detailed analysis.
     */
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

    /**
     * Determines the effective namespace to use for the ontology.
     * Falls back to DEFAULT_NS if vocabulary metadata namespace is invalid.
     */
    private String determineEffectiveNamespace(VocabularyMetadata metadata) {
        String namespace = metadata.getNamespace();
        if (namespace != null && !namespace.trim().isEmpty() && UtilityMethods.isValidUrl(namespace)) {
            return UtilityMethods.ensureNamespaceEndsWithDelimiter(namespace);
        }
        return DEFAULT_NS;
    }

    /**
     * Creates model properties map for export metadata.
     */
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
}
