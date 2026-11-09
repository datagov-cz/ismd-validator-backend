package com.dia.transformer;

import com.dia.conversion.data.ClassData;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.data.VocabularyMetadata;
import com.dia.conversion.transformer.ConceptFilterUtil;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VocabularyIdentifierExportTest {

    private static final String VOCABULARY_IRI = "https://slovník.gov.cz/generický/slovník-pro-agendu-a824-patenty";
    private static final String CONCEPT_IRI = VOCABULARY_IRI + "/pojem/patentový-spis";

    @Test
    void exportToJson_VocabularyWithIdentifier_KeepsIdentifierAndItsConcepts() throws Exception {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Slovník pro agendu A824 Patenty");
        metadata.setIdentifier(VOCABULARY_IRI);
        metadata.setNamespace(VOCABULARY_IRI);

        ClassData classData = new ClassData();
        classData.setName("Patentový spis");
        classData.setIdentifier(CONCEPT_IRI);

        OntologyData ontologyData = OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(List.of(classData))
                .properties(List.of())
                .relationships(List.of())
                .hierarchies(List.of())
                .build();

        OFNDataTransformerNew transformer = new OFNDataTransformerNew(new ConceptFilterUtil());
        TransformationResult result = transformer.transform(ontologyData);
        JsonNode json = new ObjectMapper().readTree(transformer.exportToJson(result));

        assertEquals(VOCABULARY_IRI, json.get("iri").asText());
        assertEquals(1, json.get("pojmy").size());
        assertEquals(CONCEPT_IRI, json.get("pojmy").get(0).get("iri").asText());
    }
}
