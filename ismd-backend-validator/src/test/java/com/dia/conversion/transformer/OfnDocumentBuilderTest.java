package com.dia.conversion.transformer;

import com.dia.conversion.data.*;
import com.dia.ofn.ConformanceIssue;
import com.dia.ofn.ConformanceIssue.Kind;
import com.dia.ofn.OfnSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Input rules of {@link OfnDocumentBuilder}: how reader values become OFN terms, and that every
 * value it cannot map is reported.
 */
class OfnDocumentBuilderTest {

    private static final String NS = "https://slovník.gov.cz/";
    private static final String VOCABULARY = NS + "test";
    private static final String P = VOCABULARY + "/pojem/";
    private static final String ELI_SOURCE = "https://www.e-sbirka.cz/eli/cz/sb/2000/361/2024-07-01/dokument/norma/par_2";
    private static final String ELI_PROVISION =
            "https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/2000/361/2024-07-01/dokument/norma/par_2";

    private final OfnDocumentBuilder builder =
            new OfnDocumentBuilder(new ConceptFilterUtil(), OfnSpecification.bundled());

    private final List<ClassData> classes = new ArrayList<>();
    private final List<PropertyData> properties = new ArrayList<>();
    private final List<RelationshipData> relationships = new ArrayList<>();
    private final List<HierarchyData> hierarchies = new ArrayList<>();

    @Test
    void vocabulary_hasContextIriTypesNameAndDates() {
        OfnDocumentBuilder.Result result = build();

        JsonNode document = result.document();
        assertThat(document.get("@context").asText()).isEqualTo(OfnSpecification.bundled().contextUrl());
        assertThat(document.get("iri").asText()).isEqualTo(VOCABULARY);
        assertThat(texts(document.get("typ"))).containsExactly("Slovník", "Tezaurus", "Konceptuální model");
        assertThat(document.at("/název/cs").asText()).isEqualTo("test");
        assertThat(document.at("/vytvořeno/typ").asText()).isEqualTo("Časový okamžik");
        assertThat(document.at("/vytvořeno/datum_a_čas").asText()).isEqualTo("2026-01-15T10:30:00+01:00");
        assertThat(document.at("/aktualizováno/datum").asText()).isEqualTo("2026-01-10");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void keys_followContextOrder() {
        ClassData a = cls("A");
        a.setDefinition("definice");
        a.setDescription("popis");
        a.setAlternativeName("jiné");

        List<String> keys = new ArrayList<>();
        concept(build(), "a").fieldNames().forEachRemaining(keys::add);

        assertThat(keys).containsExactly("iri", "typ", "název", "alternativní-název", "popis", "definice");
    }

    @Test
    void classType_subjectOfLaw() {
        cls("A").setType("Subjekt práva");

        assertThat(texts(concept(build(), "a").get("typ")))
                .containsExactly("Pojem", "Koncept", "Třída", "Typ subjektu práva");
    }

    @Test
    void privacyProvisionWithEli_makesDataNonPublic() {
        cls("A").setPrivacyProvision(ELI_SOURCE);

        JsonNode a = concept(build(), "a");

        assertThat(texts(a.get("typ"))).contains("Neveřejný údaj");
        assertThat(texts(a.get("ustanovení-dokládající-neveřejnost-údaje"))).containsExactly(ELI_PROVISION);
    }

    @Test
    void privacyProvisionWithoutEli_isReported_andDataIsNotMarkedNonPublic() {
        cls("A").setPrivacyProvision("§ 5 zákona");

        OfnDocumentBuilder.Result result = build();

        assertThat(texts(concept(result, "a").get("typ"))).doesNotContain("Neveřejný údaj");
        assertThat(only(result, Kind.VALUE_DROPPED).location()).isEqualTo("pojmy[a]/ustanovení-dokládající-neveřejnost-údaje");
    }

    @Test
    void publicData_isMarkedPublic() {
        cls("A").setIsPublic("Ano");

        assertThat(texts(concept(build(), "a").get("typ"))).contains("Veřejný údaj");
    }

    @Test
    void agendaAndAis_codesBecomeRppIris_invalidCodesAreReported() {
        ClassData a = cls("A");
        a.setAgendaCode("A998");
        a.setAgendaSystemCode("1234");
        ClassData b = cls("B");
        b.setAgendaCode("agenda X");

        OfnDocumentBuilder.Result result = build();

        assertThat(concept(result, "a").get("agenda").asText())
                .isEqualTo("https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A998");
        assertThat(concept(result, "a").get("agendový-informační-systém").asText())
                .isEqualTo("https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/1234");
        assertThat(concept(result, "b").has("agenda")).isFalse();
        assertThat(only(result, Kind.VALUE_DROPPED).conceptIri()).isEqualTo(P + "b");
    }

    @Test
    void governanceValues_becomeCodelistCuries_unknownValuesAreReported() {
        ClassData a = cls("A");
        a.setAcquisitionMethod("vlastní");
        a.setSharingMethod("veřejně přístupné");
        a.setContentType("nějaký");

        OfnDocumentBuilder.Result result = build();

        JsonNode concept = concept(result, "a");
        assertThat(concept.get("způsob-získání-údaje").asText()).isEqualTo("způsoby-získání:vlastní");
        assertThat(texts(concept.get("způsoby-sdílení-údaje"))).containsExactly("způsoby-sdílení:veřejně-přístupné");
        assertThat(concept.has("typ-obsahu-údaje")).isFalse();
        assertThat(only(result, Kind.VALUE_DROPPED).location()).isEqualTo("pojmy[a]/typ-obsahu-údaje");
    }

    @Test
    void sources_eliBecomesProvision_urlAndTextBecomeDigitalObjects() {
        cls("A").setSource(ELI_SOURCE + "; https://example.org/dokument; Směrnice EU");

        JsonNode a = concept(build(), "a");

        assertThat(texts(a.get("definující-ustanovení-právního-předpisu"))).containsExactly(ELI_PROVISION);
        JsonNode documents = a.get("definující-nelegislativní-zdroj");
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).get("typ").asText()).isEqualTo("Digitální objekt");
        assertThat(documents.get(0).get("url").asText()).isEqualTo("https://example.org/dokument");
        assertThat(documents.get(1).at("/název/cs").asText()).isEqualTo("Směrnice EU");
    }

    @Test
    void sharedInPpdf_isBoolean_otherValuesAreReported() {
        cls("Doména");
        prop("a", "Doména", null).setSharedInPPDF("ano");
        prop("b", "Doména", null).setSharedInPPDF("možná");

        OfnDocumentBuilder.Result result = build();

        assertThat(concept(result, "a").get("je-sdílen-v-ppdf").asBoolean()).isTrue();
        assertThat(concept(result, "b").has("je-sdílen-v-ppdf")).isFalse();
        assertThat(only(result, Kind.VALUE_DROPPED).conceptIri()).isEqualTo(P + "b");
    }

    @Test
    void propertyRange_mapsCzechAndXsdNames_defaultsToLiteral() {
        cls("Doména");
        prop("a", "Doména", "Celé číslo");
        prop("b", "Doména", null);
        prop("c", "Doména", "xsd:neexistuje");
        prop("d", "Doména", "xsd:date");

        OfnDocumentBuilder.Result result = build();

        assertThat(concept(result, "a").get("obor-hodnot").asText()).isEqualTo("xsd:integer");
        assertThat(concept(result, "b").get("obor-hodnot").asText()).isEqualTo("http://www.w3.org/2000/01/rdf-schema#Literal");
        assertThat(concept(result, "c").get("obor-hodnot").asText()).isEqualTo("http://www.w3.org/2000/01/rdf-schema#Literal");
        assertThat(concept(result, "d").get("obor-hodnot").asText()).isEqualTo("xsd:date");
    }

    @Test
    void domainAndRange_byName_resolveToConceptIris_includingExternalClasses() {
        cls("Řidič");
        ClassData external = cls("Adresa");
        external.setIdentifier("https://slovník.gov.cz/jiný/pojem/adresa");
        RelationshipData relationship = new RelationshipData();
        relationship.setName("bydlí na");
        relationship.setDomain("Řidič");
        relationship.setRange("Adresa");
        relationships.add(relationship);

        OfnDocumentBuilder.Result result = build();

        JsonNode concept = concept(result, "bydlí-na");
        assertThat(concept.get("definiční-obor").asText()).isEqualTo(P + "řidič");
        assertThat(concept.get("obor-hodnot").asText()).isEqualTo("https://slovník.gov.cz/jiný/pojem/adresa");
        assertThat(iris(result)).doesNotContain("https://slovník.gov.cz/jiný/pojem/adresa");
    }

    @Test
    void hierarchy_usesTheTermForTheSubConceptKind() {
        cls("Doména");
        cls("Nad");
        cls("Pod");
        prop("nadvlastnost", "Doména", null);
        prop("podvlastnost", "Doména", null);
        hierarchy("Pod", "Nad");
        hierarchy("podvlastnost", "nadvlastnost");

        OfnDocumentBuilder.Result result = build();

        assertThat(texts(concept(result, "pod").get("nadřazená-třída"))).containsExactly(P + "nad");
        assertThat(texts(concept(result, "podvlastnost").get("nadřazená-vlastnost"))).containsExactly(P + "nadvlastnost");
    }

    @Test
    void codelistWithoutNkodDataset_isReported() {
        cls("A").setCodelistIri("https://data.gov.cz/zdroj/číselník/x");

        OfnDocumentBuilder.Result result = build();

        assertThat(concept(result, "a").has("instance-definovány-číselníkem")).isFalse();
        assertThat(only(result, Kind.VALUE_DROPPED).location()).isEqualTo("pojmy[a]/instance-definovány-číselníkem");
    }

    @Test
    void iriCollision_keepsFirstElement_andReportsTheSecond() {
        cls("A").setDefinition("první");
        cls("A").setDefinition("druhá");

        OfnDocumentBuilder.Result result = build();

        assertThat(iris(result)).containsExactly(P + "a");
        assertThat(concept(result, "a").at("/definice/cs").asText()).isEqualTo("první");
        assertThat(only(result, Kind.CONCEPT_EXCLUDED).conceptIri()).isEqualTo(P + "a");
    }

    @Test
    void classWithoutName_isReported() {
        ClassData unnamed = new ClassData();
        unnamed.setId("EAID_1");
        classes.add(unnamed);

        OfnDocumentBuilder.Result result = build();

        assertThat(iris(result)).isEmpty();
        assertThat(only(result, Kind.CONCEPT_EXCLUDED).location()).contains("EAID_1");
    }

    // ---------------------------------------------------------------- input builders

    private ClassData cls(String name) {
        ClassData data = new ClassData();
        data.setName(name);
        classes.add(data);
        return data;
    }

    private PropertyData prop(String name, String domain, String dataType) {
        PropertyData data = new PropertyData();
        data.setName(name);
        data.setDomain(domain);
        data.setDataType(dataType);
        properties.add(data);
        return data;
    }

    private void hierarchy(String sub, String sup) {
        HierarchyData data = new HierarchyData();
        data.setSubClass(sub);
        data.setSuperClass(sup);
        hierarchies.add(data);
    }

    private OfnDocumentBuilder.Result build() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("test");
        metadata.setDateOfCreation("2026-01-15T10:30:00+01:00");
        metadata.setDateOfModification("2026-01-10");
        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(classes)
                .properties(properties)
                .relationships(relationships)
                .hierarchies(hierarchies)
                .build();
        return builder.build(data, NS);
    }

    // ---------------------------------------------------------------- result helpers

    private static JsonNode concept(OfnDocumentBuilder.Result result, String localName) {
        return StreamSupport.stream(result.document().get("pojmy").spliterator(), false)
                .filter(concept -> concept.get("iri").asText().equals(P + localName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no concept " + localName + " in " + iris(result)));
    }

    private static List<String> iris(OfnDocumentBuilder.Result result) {
        return texts(result.document().get("pojmy").findValues("iri"));
    }

    private static ConformanceIssue only(OfnDocumentBuilder.Result result, Kind kind) {
        List<ConformanceIssue> issues = result.issues().stream().filter(i -> i.kind() == kind).toList();
        assertThat(issues).as("issues of kind " + kind + " in " + result.issues()).hasSize(1);
        return issues.get(0);
    }

    private static List<String> texts(Iterable<JsonNode> nodes) {
        List<String> values = new ArrayList<>();
        nodes.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static List<String> texts(JsonNode node) {
        return texts((Iterable<JsonNode>) node);
    }
}
