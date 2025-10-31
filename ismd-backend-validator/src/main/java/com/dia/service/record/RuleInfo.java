package com.dia.service.record;

/**
 * Information about a validation rule
 */
public record RuleInfo(
        String name,
        String filename,
        boolean enabled,
        java.util.Map<String, String> metadata
) {
}
