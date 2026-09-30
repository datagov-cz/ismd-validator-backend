package com.dia.validation.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the local/global rule classifier. Global rules must be recognised by the
 * {@code global-} prefix so they are routed to the corpus engine and excluded from the
 * in-memory Jena pass (which would otherwise re-emit the old false positives).
 */
class RuleManagerClassifierTest {

    @Test
    void globalRulesAreRecognisedByPrefix() {
        assertThat(RuleManager.isGlobalRule("global-pojem-se-stejným-iri")).isTrue();
        assertThat(RuleManager.isGlobalRule("global-slovník-s-jiným-iri-stejným-názvem")).isTrue();
    }

    @Test
    void localRulesAndOthersAreNotGlobal() {
        assertThat(RuleManager.isGlobalRule("local-charakteristiky-rpp-pojmu")).isFalse();
        assertThat(RuleManager.isGlobalRule("something-else")).isFalse();
        assertThat(RuleManager.isGlobalRule(null)).isFalse();
    }
}
