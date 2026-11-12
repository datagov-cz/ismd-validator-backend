package com.dia.conversion.data;

import com.dia.ofn.ConformanceIssue;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.util.List;

/**
 * A converted vocabulary: the OFN document that passed the conformance gate, the same data as RDF,
 * and what the conversion dropped or excluded.
 *
 * @param document          the gated OFN slovníky document; written as is to JSON-LD
 * @param model             the document read as RDF; written to Turtle and validated with SHACL
 * @param conformanceIssues what the conversion dropped or excluded, and why
 */
public record TransformationResult(ObjectNode document, Model model, List<ConformanceIssue> conformanceIssues,
                                   String modelName, String effectiveNamespace) {

    public TransformationResult {
        conformanceIssues = List.copyOf(conformanceIssues);
    }

    /** The model as an {@link OntModel} view (no inference), for callers that need one. */
    public OntModel getOntModel() {
        return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
    }
}
