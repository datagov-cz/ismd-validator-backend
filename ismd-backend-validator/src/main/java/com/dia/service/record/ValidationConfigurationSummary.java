package com.dia.service.record;

import com.dia.enums.ValidationTiming;

import java.util.List;

public record ValidationConfigurationSummary(
        int totalRules,
        int enabledRules,
        List<String> allEnabledRules,
        ValidationTiming defaultTiming
) {}
