package com.dia.converter.excel.mapper;

@FunctionalInterface
public interface PropertySetter<T> {
    void setProperty(T object, String value);
}
