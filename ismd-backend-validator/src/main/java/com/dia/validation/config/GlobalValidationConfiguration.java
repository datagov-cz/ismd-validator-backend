package com.dia.validation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code validation.global.*} — external SPARQL corpus access for the global
 * (cross-vocabulary) validation rules: {@code validation.global.sparql.*} +
 * {@code validation.global.circuit-breaker.*}.
 *
 * <p>There is no retry/max-attempts config — resilience is timeout + circuit breaker +
 * lenient fail-open. Note that <em>which</em> global rules are enabled is NOT configured
 * here: that is governed by {@code validation.rules.enabled.*} via
 * {@link ValidationConfiguration#isRuleEnabled} (see
 * {@link RuleManager#getEnabledGlobalRuleNames()}).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "validation.global")
public class GlobalValidationConfiguration {

    private Sparql sparql = new Sparql();

    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Data
    public static class Sparql {
        /** Corpus SPARQL endpoint. Empty (default) disables all global checks (fail-open). */
        private String endpoint = "";
        /** Per-query timeout, milliseconds. */
        private int timeout = 10000;
    }

    @Data
    public static class CircuitBreaker {
        /** Consecutive corpus failures before the breaker opens. */
        private int failureThreshold = 5;
        /** How long the breaker stays open before a trial call, milliseconds. */
        private long cooldownMs = 30000;
    }
}
