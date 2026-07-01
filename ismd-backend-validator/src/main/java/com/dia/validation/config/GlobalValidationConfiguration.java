package com.dia.validation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Binds {@code validation.global.*}. Two concerns:
 * <ul>
 *   <li>Per-rule enable flags ({@code validation.global.enabled.*}) — controls which
 *       global (corpus) rules run.</li>
 *   <li>External SPARQL corpus access ({@code validation.global.sparql.*} +
 *       {@code validation.global.circuit-breaker.*}) — shaped to mirror
 *       {@code ismd-tool-backend}'s {@code NkdConfig} (hard rule: follow that module's
 *       external-SPARQL integration exactly). There is deliberately no retry/max-attempts
 *       config — resilience is timeout + lenient fail-open + circuit breaker.</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "validation.global")
public class GlobalValidationConfiguration {

    private Map<String, Boolean> enabled = new HashMap<>();

    private Sparql sparql = new Sparql();

    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Data
    public static class Sparql {
        /** Corpus SPARQL endpoint. Empty (default) disables all global checks (fail-open). */
        private String endpoint = "";
        /** Per-query timeout, milliseconds. */
        private int timeout = 10000;
        /** Bound on concurrent corpus requests when a lookup is fanned out per node. */
        private int maxConcurrentRequests = 4;
    }

    @Data
    public static class CircuitBreaker {
        /** Consecutive corpus failures before the breaker opens. */
        private int failureThreshold = 5;
        /** How long the breaker stays open before a trial call, milliseconds. */
        private long cooldownMs = 30000;
    }

    public boolean isRuleEnabled(String ruleName) {
        return enabled.getOrDefault(ruleName, true);
    }

    public void setRuleEnabled(String ruleName, boolean value) {
        this.enabled.put(ruleName, value);
    }

    public Set<String> getEnabledRuleNames() {
        return enabled.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
