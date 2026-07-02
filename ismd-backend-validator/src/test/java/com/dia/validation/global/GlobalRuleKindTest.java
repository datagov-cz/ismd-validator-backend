package com.dia.validation.global;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@link GlobalRuleKind#forRuleName} dispatch table against the actual
 * {@code global-*.ttl} rule files. {@code GlobalRuleKind.BY_RULE_NAME} is keyed by exact
 * (accented) rule names; if a rule file is renamed or added without updating the map, the
 * only production symptom is a silent {@code log.warn(... "no registered kind")} and the
 * rule not running. This test turns that into a build failure.
 */
class GlobalRuleKindTest {

    private List<String> globalRuleNames() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:validation/rules/global-*.ttl");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(f -> f != null)
                .map(f -> f.substring(0, f.length() - ".ttl".length()).toLowerCase())
                .toList();
    }

    @Test
    void everyGlobalRuleFileMapsToAKind() throws Exception {
        List<String> names = globalRuleNames();
        assertThat(names).hasSize(6); // the six known global rules

        for (String name : names) {
            assertThat(GlobalRuleKind.forRuleName(name))
                    .as("GlobalRuleKind mapping for rule file '%s'", name)
                    .isNotNull();
        }
    }
}
