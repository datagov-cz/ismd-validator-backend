package com.dia.ofn;

import com.dia.ofn.ConformanceIssue.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class OfnConformanceGateTest {

    private static final String NS = "https://slovník.gov.cz/test/pojem/";

    private final ObjectMapper mapper = new ObjectMapper();
    private final OfnConformanceGate gate = new OfnConformanceGate(OfnSpecification.bundled());

    @ParameterizedTest
    @ValueSource(strings = {
            "com/dia/expected-outputs/complete/excel_output_jsonld_no-lkod.jsonld",
            "com/dia/expected-outputs/complete/archi_output_jsonld_no-lkod.jsonld",
            "com/dia/expected-outputs/complete/ea_output_jsonld_no-lkod.jsonld"
    })
    void conformantOutput_passesUnchanged(String path) throws IOException {
        ObjectNode document = read(path);

        GateResult result = gate.apply(document);

        assertThat(result.issues()).isEmpty();
        assertThat(result.document()).isEqualTo(document);
    }

    @Test
    void input_isNotModified() {
        ObjectNode document = vocabulary(třída("a").put("neznámé-pole", "x"));
        ObjectNode before = document.deepCopy();

        gate.apply(document);

        assertThat(document).isEqualTo(before);
    }

    @Test
    void keyNotInContext_isDropped() {
        GateResult result = gate.apply(vocabulary(třída("a").put("neznámé-pole", "x")));

        assertThat(concept(result, "a").has("neznámé-pole")).isFalse();
        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.conceptIri()).isEqualTo(NS + "a");
                    assertThat(issue.location()).isEqualTo("pojmy[a]/neznámé-pole");
                });
    }

    @Test
    void nonIriValue_inIdTerm_isDropped_otherValuesKept() {
        ObjectNode a = třída("a");
        a.putArray("ekvivalentní-pojem").add("není iri").add("https://example.org/b");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(concept(result, "a").get("ekvivalentní-pojem"))
                .containsExactly(mapper.getNodeFactory().textNode("https://example.org/b"));
        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/a|b", "https://example.org/<b>", "https://example.org/\"b\"",
            "https://example.org/b%zz", "https://example.org/b?x=[1]"})
    void iriTheSchemaWouldReject_isDroppedAlone_conceptKept(String badIri) {
        ObjectNode a = třída("a");
        a.putArray("ekvivalentní-pojem").add(badIri).add("https://example.org/ok");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(concept(result, "a").get("ekvivalentní-pojem"))
                .containsExactly(mapper.getNodeFactory().textNode("https://example.org/ok"));
        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED)).singleElement()
                .satisfies(issue -> assertThat(issue.message()).startsWith("hodnota není IRI"));
        assertThat(result.issuesOfKind(Kind.CONCEPT_EXCLUDED)).isEmpty();
    }

    @Test
    void unicodeIri_isKept() {
        ObjectNode a = třída("a");
        a.putArray("ekvivalentní-pojem").add("https://slovník.gov.cz/jiný/pojem/řidič");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(result.issues()).isEmpty();
    }

    @Test
    void singleValue_inSetTerm_isWrapped_singleItemArray_inSingleTerm_isUnwrapped() {
        ObjectNode a = třída("a").put("ekvivalentní-pojem", "https://example.org/b");
        a.putArray("agenda").add("https://example.org/agenda");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(concept(result, "a").get("ekvivalentní-pojem").isArray()).isTrue();
        assertThat(concept(result, "a").get("agenda").asText()).isEqualTo("https://example.org/agenda");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void wrongBooleanAndLanguageShapes_areDropped() {
        ObjectNode a = vlastnost("a", "b").put("je-sdílen-v-ppdf", "ano");
        a.putObject("definice").put("česky", "text");

        GateResult result = gate.apply(vocabulary(třída("b"), a));

        assertThat(concept(result, "a").has("je-sdílen-v-ppdf")).isFalse();
        assertThat(concept(result, "a").has("definice")).isFalse();
        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED)).hasSize(2);
        assertThat(result.issuesOfKind(Kind.CONCEPT_EXCLUDED)).isEmpty();
    }

    @Test
    void blankOrNonTextLanguageValues_areReported() {
        ObjectNode a = třída("a");
        a.putObject("alternativní-název").putArray("cs").add("Řidič").add("  ").add(42);

        GateResult result = gate.apply(vocabulary(a));

        assertThat(concept(result, "a").get("alternativní-název").get("cs"))
                .containsExactly(mapper.getNodeFactory().textNode("Řidič"));
        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED))
                .hasSize(2)
                .allMatch(issue -> issue.location().equals("pojmy[a]/alternativní-název/cs"));
    }

    /** A blank name is reported as dropped, so the exclusion that follows has a visible cause. */
    @Test
    void blankName_isReportedBeforeTheConceptIsExcluded() {
        ObjectNode a = třída("a");
        a.putObject("název").put("cs", "   ");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED))
                .singleElement()
                .satisfies(issue -> assertThat(issue.location()).isEqualTo("pojmy[a]/název/cs"));
        assertThat(result.issuesOfKind(Kind.CONCEPT_EXCLUDED)).hasSize(1);
    }

    @Test
    void unknownType_isDropped() {
        ObjectNode a = třída("a");
        ((ArrayNode) a.get("typ")).add("Neexistující typ");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(types(concept(result, "a"))).doesNotContain("Neexistující typ");
        assertThat(result.issuesOfKind(Kind.VALUE_DROPPED)).hasSize(1);
    }

    @Test
    void vlastnostWithoutDomain_isExcluded() {
        ObjectNode a = vlastnost("a", "b");
        a.remove("definiční-obor");

        GateResult result = gate.apply(vocabulary(třída("b"), a));

        assertThat(iris(result)).containsExactly(NS + "b");
        assertThat(result.issuesOfKind(Kind.CONCEPT_EXCLUDED))
                .singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("definiční-obor"));
    }

    @Test
    void nonPublicDataWithoutProvision_isExcluded() {
        ObjectNode a = třída("a");
        ((ArrayNode) a.get("typ")).add("Neveřejný údaj");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(iris(result)).isEmpty();
        assertThat(result.issuesOfKind(Kind.CONCEPT_EXCLUDED)).hasSize(1);
    }

    @Test
    void conceptWithoutCzechName_isExcluded() {
        ObjectNode a = třída("a");
        a.putObject("název").put("en", "A");

        GateResult result = gate.apply(vocabulary(a));

        assertThat(iris(result)).isEmpty();
    }

    @Test
    void exclusion_cascades_throughRequiredReferences() {
        ObjectNode b = třída("b");
        b.remove("název");                        // b fails the schema
        ObjectNode a = vlastnost("a", "b");       // a's only domain is b
        ObjectNode c = třída("c");
        c.putArray("nadřazená-třída").add(NS + "b"); // optional reference to b

        GateResult result = gate.apply(vocabulary(b, a, c));

        assertThat(iris(result)).containsExactly(NS + "c");
        assertThat(concept(result, "c").has("nadřazená-třída")).isFalse();
        assertThat(result.issuesOfKind(Kind.CONCEPT_EXCLUDED))
                .extracting(ConformanceIssue::conceptIri)
                .containsExactly(NS + "b", NS + "a");
        assertThat(result.issuesOfKind(Kind.REFERENCE_REMOVED))
                .extracting(ConformanceIssue::conceptIri)
                .containsExactlyInAnyOrder(NS + "a", NS + "c");
    }

    /**
     * A future context may put a concept reference inside a nested object. Exclusion removes it there too,
     * while other references in the same object (here an NKOD-like dataset) stay.
     */
    @Test
    void exclusion_removesNestedReferences_andKeepsUnrelatedOnes() {
        OfnConformanceGate gateWithNested = new OfnConformanceGate(specificationWithNestedReference());
        ObjectNode a = vlastnost("a", "c");
        a.remove("definiční-obor");                // a fails the schema
        ObjectNode b = třída("b");
        ArrayNode links = b.putArray("vazba");
        links.addObject().put("cíl", NS + "a").put("datová-sada", "https://data.gov.cz/zdroj/datové-sady/1");
        links.addObject().put("cíl", NS + "c");
        links.addObject().put("cíl", NS + "a");    // nothing else left: the object goes

        GateResult result = gateWithNested.apply(vocabulary(a, b, třída("c")));

        assertThat(iris(result)).containsExactly(NS + "b", NS + "c");
        JsonNode kept = concept(result, "b").get("vazba");
        assertThat(kept).hasSize(2);
        assertThat(kept.get(0).has("cíl")).isFalse();
        assertThat(kept.get(0).get("datová-sada").asText()).isEqualTo("https://data.gov.cz/zdroj/datové-sady/1");
        assertThat(kept.get(1).get("cíl").asText()).isEqualTo(NS + "c");
        assertThat(result.issuesOfKind(Kind.REFERENCE_REMOVED))
                .allMatch(issue -> NS.concat("b").equals(issue.conceptIri()))
                .extracting(ConformanceIssue::location)
                .containsExactly("pojmy[b]/vazba[0]/cíl", "pojmy[b]/vazba[2]/cíl");
    }

    /** A reference written as a compact IRI still matches the excluded concept's full IRI. */
    @Test
    void exclusion_removesCompactIriReferences() {
        OfnConformanceGate gateWithNested = new OfnConformanceGate(specificationWithNestedReference());
        ObjectNode a = třída("a");
        a.remove("název");                         // a fails the schema
        ObjectNode b = třída("b");
        b.putArray("nadřazená-třída").add("test:a").add("test:c");

        GateResult result = gateWithNested.apply(vocabulary(a, b, třída("c")));

        assertThat(concept(result, "b").get("nadřazená-třída"))
                .containsExactly(mapper.getNodeFactory().textNode("test:c"));
        assertThat(result.issuesOfKind(Kind.REFERENCE_REMOVED)).singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("test:a"));
    }

    @Test
    void vocabularyWithoutName_isReportedInvalid() {
        ObjectNode document = vocabulary(třída("a"));
        document.remove("název");

        GateResult result = gate.apply(document);

        assertThat(result.isVocabularyValid()).isFalse();
        assertThat(iris(result)).containsExactly(NS + "a");
    }

    @Test
    void validVocabulary_isValid() {
        assertThat(gate.apply(vocabulary(třída("a"))).isVocabularyValid()).isTrue();
    }

    // ---------------------------------------------------------------- builders

    private ObjectNode vocabulary(ObjectNode... concepts) {
        ObjectNode document = mapper.createObjectNode();
        document.put("@context", OfnSpecification.bundled().contextUrl());
        document.put("iri", "https://slovník.gov.cz/test");
        document.putArray("typ").add("Slovník").add("Tezaurus").add("Konceptuální model");
        document.putObject("název").put("cs", "Testovací slovník");
        ArrayNode pojmy = document.putArray("pojmy");
        for (ObjectNode concept : concepts) {
            pojmy.add(concept);
        }
        return document;
    }

    private ObjectNode třída(String name) {
        ObjectNode concept = mapper.createObjectNode();
        concept.put("iri", NS + name);
        concept.putArray("typ").add("Pojem").add("Koncept").add("Třída");
        concept.putObject("název").put("cs", name);
        return concept;
    }

    private ObjectNode vlastnost(String name, String domain) {
        ObjectNode concept = mapper.createObjectNode();
        concept.put("iri", NS + name);
        concept.putArray("typ").add("Pojem").add("Koncept").add("Vlastnost");
        concept.putObject("název").put("cs", name);
        concept.put("definiční-obor", NS + domain);
        concept.put("obor-hodnot", "xsd:string");
        return concept;
    }

    /**
     * The bundled specification with a test prefix and a hypothetical nested term on concepts:
     * {@code vazba} (a set of objects) holding a concept reference {@code cíl} and a non-concept
     * reference {@code datová-sada}.
     */
    private OfnSpecification specificationWithNestedReference() {
        OfnSpecification bundled = OfnSpecification.bundled();
        ObjectNode contextDocument = bundled.contextDocument().deepCopy();
        ObjectNode context = (ObjectNode) contextDocument.get("@context");
        context.put("test", NS);
        ObjectNode conceptContext = (ObjectNode) context.get("pojmy").get("@context");
        ObjectNode vazba = conceptContext.putObject("vazba");
        vazba.put("@id", "https://example.org/vazba").put("@container", "@set");
        ObjectNode vazbaContext = vazba.putObject("@context");
        vazbaContext.putObject("cíl").put("@id", "https://example.org/cíl").put("@type", "@id");
        vazbaContext.putObject("datová-sada").put("@id", "https://example.org/datová-sada").put("@type", "@id");
        return new OfnSpecification(contextDocument, bundled.schemaDocument());
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> iris(GateResult result) {
        return StreamSupport.stream(result.document().get("pojmy").spliterator(), false)
                .map(concept -> concept.get("iri").asText())
                .toList();
    }

    private static JsonNode concept(GateResult result, String name) {
        return StreamSupport.stream(result.document().get("pojmy").spliterator(), false)
                .filter(concept -> concept.get("iri").asText().equals(NS + name))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> types(JsonNode concept) {
        return StreamSupport.stream(concept.get("typ").spliterator(), false).map(JsonNode::asText).toList();
    }

    private ObjectNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return (ObjectNode) mapper.readTree(in);
        }
    }
}
