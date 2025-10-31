package com.dia.validation.config;

import java.util.Map;

public record RuleDefinition(String name, String filename, String filePath, Map<String, String> metadata) {

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    @Override
    public String toString() {
        return String.format("RuleDefinition{name='%s', filename='%s'}", name, filename);
    }
}
