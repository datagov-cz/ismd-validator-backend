package com.dia.validation.config;

import com.dia.enums.ValidationTiming;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "validation.rules")
public class ValidationConfiguration {

    private Map<String, Boolean> enabled = new HashMap<>();

    private String rulesDirectory = "classpath:validation/rules/";

    private ValidationTiming defaultTiming = ValidationTiming.BEFORE_EXPORT;

    private long timeoutMs = 10000; // 10 seconds, execution time of one SHACL validation

    /** Max time a validation may wait for a free worker before the request is rejected as busy. */
    private long queueTimeoutMs = 30000;

    /** SHACL worker threads; 0 = max(2, available processors). */
    private int poolSize = 0;

    private boolean cacheResults = true;

    private boolean continueOnRuleError = true;

    private boolean enableOntologyViolationDownload = false;

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
