package com.dia.conversion.reader.excel.mapper;

@FunctionalInterface
public interface PropertySetter<T> {
    void setProperty(T object, String value);
}
