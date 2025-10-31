package com.dia.validation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "validation.global")
public class GlobalValidationConfiguration {

    private Map<String, Boolean> enabled = new HashMap<>();

    private String sparqlEndpoint;

    private long timeoutMs = 10000; // 10 seconds

    private int maxAttempts = 3;

    private long retryDelayMs = 1000; // 1 second

    public boolean isRuleEnabled(String ruleName) {
        return enabled.getOrDefault(ruleName, true);
    }

    public void setRuleEnabled(String ruleName, boolean enabled) {
        this.enabled.put(ruleName, enabled);
    }

    public java.util.Set<String> getEnabledRuleNames() {
        return enabled.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }
}
