package com.dia.validation.data;

import lombok.Getter;

@Getter
public enum ValidationSeverity {
    ERROR("Error"),
    WARNING("Warning"),
    INFO("Info");

    private final String displayName;

    ValidationSeverity(String displayName) {
        this.displayName = displayName;
    }
}
