package com.dia.conversion.reader.excel.mapper;

import java.util.HashMap;
import java.util.Map;

public class ColumnMappingRegistry {
    private final Map<String, ColumnMapping<?>> mappings = new HashMap<>();

    public <T> void registerMapping(String sheetName, ColumnMapping<T> mapping) {
        mappings.put(sheetName, mapping);
    }

    @SuppressWarnings("unchecked")
    public <T> ColumnMapping<T> getMapping(String sheetName) {
        return (ColumnMapping<T>) mappings.get(sheetName);
    }
}
