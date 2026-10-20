package com.dia.conversion.transformer;

import com.dia.conversion.data.*;
import com.dia.exception.ConversionException;
import com.dia.exception.JsonExportException;
import com.dia.exception.TurtleExportException;
import com.dia.exporter.JsonExporter;
import com.dia.exporter.TurtleExporter;
import com.dia.utility.URIGenerator;
import com.dia.utility.UtilityMethods;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
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

            this.ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

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
        return properties;
    }
}
