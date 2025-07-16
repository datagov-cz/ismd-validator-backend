package com.dia.conversion.data;

import lombok.Getter;

@Getter
public class ConversionResult {
    private final OntologyData ontologyData;
    private final TransformationResult transformationResult;

    public ConversionResult(OntologyData ontologyData, TransformationResult transformationResult) {
        this.ontologyData = ontologyData;
        this.transformationResult = transformationResult;
    }
}
