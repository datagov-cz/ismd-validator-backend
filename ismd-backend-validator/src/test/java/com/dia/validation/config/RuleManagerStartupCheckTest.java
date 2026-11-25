package com.dia.validation.config;

import com.dia.exception.ValidationConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Startup must fail when an enabled rule is missing, instead of validating with fewer rules. */
class RuleManagerStartupCheckTest {

    private static RuleManager manager(String dir, String... enabledRules) {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setRulesDirectory(dir);
        config.setContinueOnRuleError(true);
        for (String rule : enabledRules) {
            config.setRuleEnabled(rule, true);
        }
        return new RuleManager(config, new DefaultResourceLoader());
    }

    @Test
    void enabledRuleThatFailsToParse_failsStartup() {
        RuleManager rm = manager("classpath:validation/rules-broken/", "local-ok", "local-broken");
        assertThatThrownBy(rm::loadRules)
                .isInstanceOf(ValidationConfigurationException.class)
                .hasMessageContaining("local-broken");
    }

    @Test
    void enabledRuleThatDoesNotExist_failsStartup() {
        RuleManager rm = manager("classpath:validation/rules-ok/", "local-ok", "local-typo");
        assertThatThrownBy(rm::loadRules)
                .isInstanceOf(ValidationConfigurationException.class)
                .hasMessageContaining("local-typo");
    }

    @Test
    void noLocalRulesEnabled_failsStartup() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setRulesDirectory("classpath:validation/rules-ok/");
        config.setRuleEnabled("local-ok", false);
        RuleManager rm = new RuleManager(config, new DefaultResourceLoader());
        assertThatThrownBy(rm::loadRules).isInstanceOf(ValidationConfigurationException.class);
    }

    @Test
    void allEnabledRulesLoaded_starts() {
        RuleManager rm = manager("classpath:validation/rules-ok/", "local-ok");
        rm.loadRules();
        assertThat(rm.getEnabledRuleNames()).containsExactly("local-ok");
    }

    @Test
    void productionRuleSet_allEnabledRulesLoad() {
        // Mirrors application.properties: every rule file shipped must load.
        RuleManager rm = manager("classpath:validation/rules/");
        rm.loadRules();
        assertThat(rm.getAllRuleNames()).hasSizeGreaterThan(20);
    }
}
