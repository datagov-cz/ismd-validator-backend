package com.dia.converter.excel.transformer;

import lombok.Getter;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Resource;

import java.util.Map;

/**
 * Result container for transformation output
 */
@Getter
public class TransformationResult {
    private final OntModel ontModel;
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;
    private final String effectiveNamespace;

    public TransformationResult(OntModel ontModel, Map<String, Resource> resourceMap,
                                String modelName, Map<String, String> modelProperties,
                                String effectiveNamespace) {
        this.ontModel = ontModel;
        this.resourceMap = resourceMap;
        this.modelName = modelName;
        this.modelProperties = modelProperties;
        this.effectiveNamespace = effectiveNamespace;
    }
}
