package com.dia.validation.global;

import com.dia.validation.ValidationResult;
import com.dia.validation.ValidationSeverity;
import com.dia.validation.config.RuleManager;
import com.dia.validation.data.ISMDValidationReport;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correctness tests for {@link GlobalValidationEngine} with a mocked {@link CorpusSparqlClient}
 * and the real global rule {@code .ttl} metadata loaded from the classpath. Covers: each rule
 * fires on a crafted corpus hit; lang-scoping does not misfire on multilingual labels; and the
 * "940 gap" — a concept typed only {@code slovníky:pojem} is still extracted and checked.
 */
class GlobalValidationEngineTest {

    private static final String POJEM_TYPE =
            "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/pojem";
    private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";

    private CorpusSparqlClient client;
    private RuleManager ruleManager;
    private GlobalValidationEngine engine;

    @BeforeEach
    void setUp() {
        client = mock(CorpusSparqlClient.class);
        ruleManager = mock(RuleManager.class);
        engine = new GlobalValidationEngine(client, ruleManager);

        when(client.isConfigured()).thenReturn(true);
        // default: no corpus hits unless a test overrides
        lenient().when(client.findExistingIris(any(), anyString())).thenReturn(Set.of());
        lenient().when(client.fetchCorpusLabels(any(), anyString())).thenReturn(List.of());
        lenient().when(client.findOtherIrisWithLabel(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of());
    }

    /** Load a real global rule model and register it in the mocked RuleManager as enabled. */
    private void enableRealRule(String ruleName) {
        String file = "validation/rules/" + ruleName + ".ttl";
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            assertThat(in).as("rule file on classpath: " + file).isNotNull();
            model.read(in, null, "TTL");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(ruleManager.getEnabledGlobalRuleNames()).thenReturn(Set.of(ruleName));
        when(ruleManager.getRuleModel(ruleName)).thenReturn(Optional.of(model));
    }

    private Model uploadWith(String iri, String type, String... langLabelPairs) {
        Model m = ModelFactory.createDefaultModel();
        m.add(m.createResource(iri), RDF.type, m.createResource(type));
        for (int i = 0; i < langLabelPairs.length; i += 2) {
            m.add(m.createResource(iri),
                    m.createProperty(SKOS + "prefLabel"),
                    m.createLiteral(langLabelPairs[i + 1], langLabelPairs[i]));
        }
        return m;
    }

    @Test
    void skipsWhenEndpointNotConfigured() {
        when(client.isConfigured()).thenReturn(false);
        ISMDValidationReport report = engine.validate(ModelFactory.createDefaultModel());
        assertThat(report.results()).isEmpty();
    }

    @Test
    void conceptSameIri_firesOnCorpusHit() {
        enableRealRule("global-pojem-se-stejným-iri");
        Model upload = uploadWith("https://slovník.gov.cz/x", POJEM_TYPE, "cs", "Osoba");
        when(client.findExistingIris(any(), eq(CorpusSparqlClient.TYPE_POJEM)))
                .thenReturn(Set.of("https://slovník.gov.cz/x"));

        ISMDValidationReport report = engine.validate(upload);

        assertThat(report.results()).hasSize(1);
        ValidationResult r = report.results().get(0);
        assertThat(r.severity()).isEqualTo(ValidationSeverity.INFO);
        assertThat(r.focusNodeUri()).isEqualTo("https://slovník.gov.cz/x");
        assertThat(r.ruleName()).contains("pojem-se-stejným-iri");
    }

    @Test
    void conceptSameIri_silentWhenNoCorpusHit() {
        enableRealRule("global-pojem-se-stejným-iri");
        Model upload = uploadWith("https://slovník.gov.cz/x", POJEM_TYPE, "cs", "Osoba");
        // client returns empty by default

        assertThat(engine.validate(upload).results()).isEmpty();
    }

    @Test
    void pojemOnlyConcept_isExtracted_soThe940GapIsCovered() {
        // A concept typed ONLY slovníky:pojem (NOT skos:Concept). It must still be found and
        // checked — this is the regression guard for the ~940 pojem-only corpus concepts.
        enableRealRule("global-pojem-s-jiným-iri-stejným-názvem");
        Model upload = uploadWith("https://slovník.gov.cz/only-pojem", POJEM_TYPE, "cs", "Osoba");
        when(client.findOtherIrisWithLabel(eq("Osoba"), eq("cs"), eq(CorpusSparqlClient.TYPE_POJEM), any()))
                .thenReturn(List.of("https://slovník.gov.cz/other"));

        ISMDValidationReport report = engine.validate(upload);

        assertThat(report.results()).hasSize(1);
        assertThat(report.results().get(0).severity()).isEqualTo(ValidationSeverity.WARNING);
        assertThat(report.results().get(0).focusNodeUri()).isEqualTo("https://slovník.gov.cz/only-pojem");
    }

    @Test
    void sameIriDiffName_langScoped_doesNotFireOnMultilingualLabels() {
        // Upload carries cs+en labels; corpus publishes the SAME labels per language.
        // The lang-scoped comparison must NOT fire (this was the old 6-FP explosion).
        enableRealRule("global-pojem-se-stejným-iri-jiným-názvem");
        String iri = "https://slovník.gov.cz/x";
        Model upload = uploadWith(iri, POJEM_TYPE, "cs", "Osoba", "en", "Person");
        when(client.fetchCorpusLabels(any(), eq(CorpusSparqlClient.TYPE_POJEM))).thenReturn(List.of(
                new CorpusSparqlClient.IriLabel(iri, "cs", "Osoba"),
                new CorpusSparqlClient.IriLabel(iri, "en", "Person")));

        assertThat(engine.validate(upload).results()).isEmpty();
    }

    @Test
    void sameIriDiffName_firesWhenSameLangLabelDiffers() {
        enableRealRule("global-pojem-se-stejným-iri-jiným-názvem");
        String iri = "https://slovník.gov.cz/x";
        Model upload = uploadWith(iri, POJEM_TYPE, "cs", "Osoba");
        // corpus has a DIFFERENT cs label for the same IRI
        when(client.fetchCorpusLabels(any(), eq(CorpusSparqlClient.TYPE_POJEM))).thenReturn(List.of(
                new CorpusSparqlClient.IriLabel(iri, "cs", "Člověk")));

        ISMDValidationReport report = engine.validate(upload);

        assertThat(report.results()).hasSize(1);
        assertThat(report.results().get(0).focusNodeUri()).isEqualTo(iri);
    }

    @Test
    void vocabDiffIriSameName_firesWithViolationSeverity() {
        enableRealRule("global-slovník-s-jiným-iri-stejným-názvem");
        Model upload = uploadWith("https://slovník.gov.cz/voc", SKOS + "ConceptScheme", "cs", "Můj slovník");
        when(client.findOtherIrisWithLabel(eq("Můj slovník"), eq("cs"),
                eq(CorpusSparqlClient.TYPE_CONCEPT_SCHEME), any()))
                .thenReturn(List.of("https://slovník.gov.cz/other-voc"));

        ISMDValidationReport report = engine.validate(upload);

        assertThat(report.results()).hasSize(1);
        assertThat(report.results().get(0).severity()).isEqualTo(ValidationSeverity.ERROR);
    }
}
