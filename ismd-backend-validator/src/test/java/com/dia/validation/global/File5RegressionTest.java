package com.dia.validation.global;

import com.dia.validation.config.RuleManager;
import com.dia.validation.data.ISMDValidationReport;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression over {@code file-5.ttl} — the upload whose multilingual concept
 * (labels in {@code @sk}/{@code @en}/{@code @cs}) used to trigger the same-IRI-different-name
 * false-positive explosion, and whose self-referential concepts tripped the always-fires
 * self-join rules.
 *
 * <p>With a corpus that does NOT publish the file's IRIs/labels (verified live: all three
 * ASK probes return false), the six global rules must produce ZERO hits. This proves the
 * false positives are gone — the broken in-memory SHACL bodies no longer run, and the
 * corpus lookups correctly find nothing. The corpus is stubbed empty here so the test is
 * deterministic (no network).
 */
class File5RegressionTest {

    private CorpusSparqlClient client;
    private RuleManager ruleManager;
    private GlobalValidationEngine engine;

    private static final List<String> ALL_GLOBAL_RULES = List.of(
            "global-pojem-se-stejným-iri",
            "global-pojem-se-stejným-iri-jiným-názvem",
            "global-pojem-s-jiným-iri-stejným-názvem",
            "global-slovník-se-stejným-iri",
            "global-slovník-se-stejným-iri-jiným-názvem",
            "global-slovník-s-jiným-iri-stejným-názvem");

    @BeforeEach
    void setUp() {
        client = mock(CorpusSparqlClient.class);
        ruleManager = mock(RuleManager.class);
        engine = new GlobalValidationEngine(client, ruleManager);

        when(client.isConfigured()).thenReturn(true);
        // Empty corpus: no IRI exists, no label matches — the real-world result for file-5's IRIs.
        when(client.findExistingIris(any(), anyString())).thenReturn(Set.of());
        when(client.fetchCorpusLabels(any(), anyString())).thenReturn(List.of());
        when(client.findOtherIrisByLabel(any(), anyString(), any())).thenReturn(Map.of());

        when(ruleManager.getEnabledGlobalRuleNames())
                .thenReturn(new java.util.LinkedHashSet<>(ALL_GLOBAL_RULES));
        for (String rule : ALL_GLOBAL_RULES) {
            when(ruleManager.getRuleModel(rule)).thenReturn(Optional.of(loadRule(rule)));
        }
    }

    @Test
    void file5_producesNoGlobalHits_whenCorpusDoesNotPublishItsIris() {
        Model upload = loadFixture();

        ISMDValidationReport report = engine.validate(upload);

        assertThat(report.results())
                .as("file-5.ttl must yield zero global false positives against an empty corpus")
                .isEmpty();
    }

    private Model loadFixture() {
        Model m = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/file-5.ttl")) {
            assertThat(in).as("fixtures/file-5.ttl on test classpath").isNotNull();
            m.read(in, null, "TTL");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return m;
    }

    private Model loadRule(String ruleName) {
        Model m = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("validation/rules/" + ruleName + ".ttl")) {
            assertThat(in).isNotNull();
            m.read(in, null, "TTL");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return m;
    }
}
