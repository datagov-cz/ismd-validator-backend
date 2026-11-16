package com.dia.transformer;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.ConceptFilterUtil;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.conversion.transformer.OfnDocumentBuilder;
import com.dia.ofn.ConformanceIssue;
import com.dia.ofn.OfnConformanceGate;
import com.dia.ofn.OfnRdf;
import com.dia.ofn.OfnSpecification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data that does not conform to OFN slovníky is not transformed at all: it is absent from the
 * JSON-LD, the Turtle and the model SHACL validates, and the result says why.
 */
class NonConformingInputTest {

    private static final String P = "https://slovník.gov.cz/test/pojem/";

    private final OFNDataTransformer transformer;

    NonConformingInputTest() {
        OfnSpecification specification = OfnSpecification.bundled();
        transformer = new OFNDataTransformer(new OfnDocumentBuilder(new ConceptFilterUtil(), specification),
                new OfnConformanceGate(specification), new OfnRdf(specification), Clock.systemUTC());
    }

    @Test
    void propertyWithoutDomain_isAbsentFromEveryOutput_andReported() throws Exception {
        ClassData vehicle = new ClassData();
        vehicle.setName("Vozidlo");
        PropertyData orphan = new PropertyData();
        orphan.setName("Barva");            // no domain: the schema requires definiční-obor
        PropertyData weight = new PropertyData();
        weight.setName("Hmotnost");
        weight.setDomain("Vozidlo");
        weight.setDataType("Desetinné číslo");

        TransformationResult result = transformer.transform(vocabulary(List.of(vehicle), List.of(orphan, weight)));

        assertThat(transformer.exportToJson(result)).contains(P + "hmotnost").doesNotContain(P + "barva");
        assertThat(transformer.exportToTurtle(result)).contains("hmotnost").doesNotContain("barva");
        assertThat(result.model().containsResource(result.model().createResource(P + "barva"))).isFalse();
        assertThat(result.getOntModel().containsResource(result.model().createResource(P + "barva"))).isFalse();

        assertThat(result.conformanceIssues())
                .filteredOn(issue -> issue.kind() == ConformanceIssue.Kind.CONCEPT_EXCLUDED)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.conceptIri()).isEqualTo(P + "barva");
                    assertThat(issue.message()).contains("definiční-obor");
                });
    }

    private static OntologyData vocabulary(List<ClassData> classes, List<PropertyData> properties) {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("test");
        return OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(classes)
                .properties(properties)
                .relationships(List.of())
                .hierarchies(List.of())
                .build();
    }
}
