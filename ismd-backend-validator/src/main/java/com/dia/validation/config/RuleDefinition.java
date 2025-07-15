package com.dia.validation.config;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Data
@RequiredArgsConstructor
@Getter
public class RuleDefinition {

    /**
     * Unique identifier for the rule (derived from filename)
     */
    private final String name;

    /**
     * Original filename of the rule file
     */
    private final String filename;

    /**
     * Full path/URI to the rule file
     */
    private final String filePath;

    /**
     * Additional metadata extracted from the SHACL model
     */
    private final Map<String, String> metadata;

    /**
     * Get metadata value by key
     * @param key metadata key
     * @return metadata value or null if not found
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Get metadata value with default
     * @param key metadata key
     * @param defaultValue default value if key not found
     * @return metadata value or default
     */
    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Check if metadata contains a specific key
     * @param key metadata key
     * @return true if key exists
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    @Override
    public String toString() {
        return String.format("RuleDefinition{name='%s', filename='%s'}", name, filename);
    }
}
