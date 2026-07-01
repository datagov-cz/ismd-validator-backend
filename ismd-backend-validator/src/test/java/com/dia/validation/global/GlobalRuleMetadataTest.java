package com.dia.validation.global;

import com.dia.validation.ValidationSeverity;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalRuleMetadataTest {

    private Model load(String ruleName) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("validation/rules/" + ruleName + ".ttl")) {
            assertThat(in).isNotNull();
            model.read(in, null, "TTL");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return model;
    }

    @Test
    void parsesInfoSeverityConceptRule() {
        Optional<GlobalRuleMetadata> meta = GlobalRuleMetadata.from(load("global-pojem-se-stejným-iri"));

        assertThat(meta).isPresent();
        assertThat(meta.get().shapeIri()).isEqualTo("https://slovník.gov.cz/shacl/globální/pojem-se-stejným-iri");
        assertThat(meta.get().severity()).isEqualTo(ValidationSeverity.INFO);
        assertThat(meta.get().message()).contains("stejným IRI");
    }

    @Test
    void parsesViolationSeverityVocabRule() {
        Optional<GlobalRuleMetadata> meta =
                GlobalRuleMetadata.from(load("global-slovník-s-jiným-iri-stejným-názvem"));

        assertThat(meta).isPresent();
        assertThat(meta.get().severity()).isEqualTo(ValidationSeverity.ERROR); // sh:Violation -> ERROR
    }

    @Test
    void parsesWarningSeverityRule() {
        Optional<GlobalRuleMetadata> meta =
                GlobalRuleMetadata.from(load("global-pojem-se-stejným-iri-jiným-názvem"));

        assertThat(meta).isPresent();
        assertThat(meta.get().severity()).isEqualTo(ValidationSeverity.WARNING);
    }

    @Test
    void emptyModelYieldsEmpty() {
        assertThat(GlobalRuleMetadata.from(ModelFactory.createDefaultModel())).isEmpty();
    }
}
