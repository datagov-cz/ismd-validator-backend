package com.dia.service.record;

import com.dia.enums.ValidationTiming;

/**
 * Summary of validation configuration
 */
public record ValidationConfigurationSummary(
        int totalRules,
        int enabledRules,
        java.util.Set<String> enabledRuleNames,
        ValidationTiming defaultTiming
) {
}
