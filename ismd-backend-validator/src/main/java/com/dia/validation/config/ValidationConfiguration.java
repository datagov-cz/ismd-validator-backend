package com.dia.validation.config;

import com.dia.validation.data.ValidationTiming;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "validation.rules")
public class ValidationConfiguration {

    /**
     * Map of rule names to their enabled status
     * Key: rule identifier (e.g., "concept-required-content")
     * Value: true if enabled, false if disabled
     */
    private Map<String, Boolean> enabled = new HashMap<>();

    /**
     * Directory containing SHACL rule files
     */
    private String rulesDirectory = "classpath:validation/rules/";

    /**
     * Default validation timing when not specified
     */
    private ValidationTiming defaultTiming = ValidationTiming.BEFORE_EXPORT;

    /**
     * Maximum validation execution time in milliseconds
     */
    private long timeoutMs = 30000; // 30 seconds

    /**
     * Whether to cache validation results
     */
    private boolean cacheResults = true;

    /**
     * Whether to continue validation if a rule fails to execute
     */
    private boolean continueOnRuleError = true;

    /**
     * Check if a specific rule is enabled
     * @param ruleName the rule identifier
     * @return true if enabled (default true if not specified)
     */
    public boolean isRuleEnabled(String ruleName) {
        return enabled.getOrDefault(ruleName, true);
    }

    /**
     * Enable or disable a specific rule
     * @param ruleName the rule identifier
     * @param enabled whether the rule should be enabled
     */
    public void setRuleEnabled(String ruleName, boolean enabled) {
        this.enabled.put(ruleName, enabled);
    }

    /**
     * Get all enabled rule names
     * @return set of enabled rule names
     */
    public java.util.Set<String> getEnabledRuleNames() {
        return enabled.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }
}
