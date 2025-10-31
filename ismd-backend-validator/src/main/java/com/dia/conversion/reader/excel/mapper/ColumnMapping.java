package com.dia.conversion.reader.excel.mapper;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
public class ColumnMapping<T> {
    private final Map<String, PropertySetter<T>> columnMappings;
    private final Map<String, PropertySetter<T>> keyValueMappings;

    private ColumnMapping(Builder<T> builder) {
        this.columnMappings = new HashMap<>(builder.columnMappings);
        this.keyValueMappings = new HashMap<>(builder.keyValueMappings);
    }

    public PropertySetter<T> getColumnMapping(String columnName) {
        return columnMappings.get(columnName);
    }

    public PropertySetter<T> getKeyValueMapping(String key) {
        return keyValueMappings.get(key);
    }

    public Set<String> getColumnNames() {
        return columnMappings.keySet();
    }

    public Set<String> getKeyNames() {
        return keyValueMappings.keySet();
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private final Map<String, PropertySetter<T>> columnMappings = new HashMap<>();
        private final Map<String, PropertySetter<T>> keyValueMappings = new HashMap<>();

        public Builder<T> withColumn(String columnName, PropertySetter<T> setter) {
            columnMappings.put(columnName, setter);
            return this;
        }

        public Builder<T> withKeyValuePair(String key, PropertySetter<T> setter) {
            keyValueMappings.put(key, setter);
            return this;
        }

        public ColumnMapping<T> build() {
            return new ColumnMapping<>(this);
        }
    }
}
