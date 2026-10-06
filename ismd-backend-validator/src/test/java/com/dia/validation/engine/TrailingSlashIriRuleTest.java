package com.dia.validation.engine;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.validation.ReportEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real {@code local-iri-končí-lomítkem.ttl} rule file against small Turtle inputs.
 */
class TrailingSlashIriRuleTest {

    private static final String RULE_FILE = "validation/rules/local-iri-končí-lomítkem.ttl";

    private static final String IRI_SHAPE = "https://slovník.gov.cz/shacl/lokální/iri-končí-lomítkem";
    private static final String REFERENCED_IRI_SHAPE = "https://slovník.gov.cz/shacl/lokální/odkazované-iri-končí-lomítkem";

    private static final String PROVISION =
            "https://e-sbirka.gov.cz/eli/cz/sb/2005/348/2026-01-01/dokument/norma/cast_1/par_2/odst_1";

    private static final String PREFIXES = """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix slovníky: <https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/> .
            @prefix ex: <https://example.com/slovnik/pojem/> .
            """;

    private static Shapes shapes;

    @BeforeAll
    static void loadRule() throws Exception {
        Model shapesModel = ModelFactory.createDefaultModel();
        try (InputStream is = TrailingSlashIriRuleTest.class.getClassLoader().getResourceAsStream(RULE_FILE)) {
            shapesModel.read(is, null, "TTL");
        }
        shapes = Shapes.parse(shapesModel.getGraph());
    }

    @Test
    void referencedIriEndingWithSlash_isReported() {
        Collection<ReportEntry> entries = validate("""
                ex:adresa a slovníky:pojem ;
                    slovníky:definující-ustanovení <%s/> .
                """.formatted(PROVISION));

        assertThat(entries).hasSize(1);
        ReportEntry entry = entries.iterator().next();
        assertThat(entry.source().getURI()).isEqualTo(REFERENCED_IRI_SHAPE);
        assertThat(entry.focusNode().getURI()).isEqualTo("https://example.com/slovnik/pojem/adresa");
        assertThat(entry.value().getURI()).isEqualTo(PROVISION + "/");
    }

    @Test
    void conceptAndVocabularyIriEndingWithSlash_areReported() {
        Collection<ReportEntry> entries = validate("""
                <https://example.com/slovnik/pojem/adresa/> a slovníky:pojem .
                <https://example.com/slovnik/> a skos:ConceptScheme .
                """);

        assertThat(entries).extracting(e -> e.source().getURI()).containsOnly(IRI_SHAPE);
        assertThat(entries).extracting(e -> e.focusNode().getURI()).containsExactlyInAnyOrder(
                "https://example.com/slovnik/pojem/adresa/",
                "https://example.com/slovnik/");
    }

    @Test
    void irisWithoutTrailingSlash_areNotReported() {
        Collection<ReportEntry> entries = validate("""
                <https://example.com/slovnik> a skos:ConceptScheme .
                ex:adresa a slovníky:pojem ;
                    skos:inScheme <https://example.com/slovnik> ;
                    skos:prefLabel "Adresa/"@cs ;
                    slovníky:definující-ustanovení <%s> .
                """.formatted(PROVISION));

        assertThat(entries).isEmpty();
    }

    private Collection<ReportEntry> validate(String turtle) {
        Model data = ModelFactory.createDefaultModel();
        data.read(new StringReader(PREFIXES + turtle), null, "TTL");
        return List.copyOf(ShaclValidator.get().validate(shapes, data.getGraph()).getEntries());
    }
}
