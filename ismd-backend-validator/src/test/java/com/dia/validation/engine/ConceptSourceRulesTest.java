package com.dia.validation.engine;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.validation.ReportEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real {@code local-zdroj-pojmu.ttl} and {@code local-definice-bez-zdroje.ttl} rule files
 * against small Turtle inputs.
 */
class ConceptSourceRulesTest {

    private static final String MISSING_SOURCE_SHAPE = "https://slovník.gov.cz/shacl/lokální/pojem-nemá-zdroj";
    private static final String DEFINITION_WITHOUT_SOURCE_SHAPE = "https://slovník.gov.cz/shacl/lokální/definice-bez-zdroje";

    private static final String CONCEPT = "https://example.com/slovnik/pojem/adresa";

    private static final String PREFIXES = """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix dct: <http://purl.org/dc/terms/> .
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix slovníky: <https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/> .
            @prefix ex: <https://example.com/slovnik/pojem/> .
            """;

    private static Shapes shapes;

    @BeforeAll
    static void loadRules() throws Exception {
        Model shapesModel = ModelFactory.createDefaultModel();
        for (String file : List.of("validation/rules/local-zdroj-pojmu.ttl", "validation/rules/local-definice-bez-zdroje.ttl")) {
            try (InputStream is = ConceptSourceRulesTest.class.getClassLoader().getResourceAsStream(file)) {
                shapesModel.read(is, null, "TTL");
            }
        }
        shapes = Shapes.parse(shapesModel.getGraph());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "slovníky:definující-ustanovení <https://e-sbirka.gov.cz/eli/cz/sb/1999/106/2025-08-19/dokument/norma/cast_1/par_2b/odst_1>",
            "slovníky:definující-nelegislativní-zdroj <https://example.com/zdroj>",
            "dct:source <https://example.com/zdroj>",
            "dc:source \"Zdroj\"@cs"
    })
    void definitionWithDefiningSource_isNotReported(String source) {
        List<ReportEntry> entries = validate("""
                ex:adresa a slovníky:pojem ;
                    skos:definition "Definice"@cs ;
                    %s .
                """.formatted(source));

        assertThat(entries).isEmpty();
    }

    @Test
    void definitionWithoutSource_isReportedByBothRules() {
        List<ReportEntry> entries = validate("""
                ex:adresa a slovníky:pojem ;
                    skos:definition "Definice"@cs .
                """);

        assertThat(entries).extracting(e -> e.source().getURI())
                .containsExactlyInAnyOrder(MISSING_SOURCE_SHAPE, DEFINITION_WITHOUT_SOURCE_SHAPE);
        assertThat(entries).extracting(e -> e.focusNode().getURI()).containsOnly(CONCEPT);
    }

    @Test
    void relatedProvisionOnly_doesNotCountAsDefiningSource() {
        List<ReportEntry> entries = validate("""
                ex:adresa a slovníky:pojem ;
                    skos:definition "Definice"@cs ;
                    slovníky:související-ustanovení <https://example.com/ustanoveni> .
                """);

        assertThat(entries).extracting(e -> e.source().getURI())
                .containsExactlyInAnyOrder(MISSING_SOURCE_SHAPE, DEFINITION_WITHOUT_SOURCE_SHAPE);
    }

    @Test
    void definingProvisionWithoutDefinition_isReportedOnce() {
        List<ReportEntry> entries = validate("""
                ex:adresa a slovníky:pojem ;
                    slovníky:definující-ustanovení <https://example.com/ustanoveni-1> , <https://example.com/ustanoveni-2> .
                """);

        assertThat(entries).extracting(e -> e.source().getURI()).containsExactly(DEFINITION_WITHOUT_SOURCE_SHAPE);
    }

    private List<ReportEntry> validate(String turtle) {
        Model data = ModelFactory.createDefaultModel();
        data.read(new StringReader(PREFIXES + turtle), null, "TTL");
        return List.copyOf(ShaclValidator.get().validate(shapes, data.getGraph()).getEntries());
    }
}
