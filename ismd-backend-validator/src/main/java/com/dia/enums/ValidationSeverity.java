package com.dia.enums;

import lombok.Getter;

@Getter
public enum ValidationSeverity {
    ERROR("Chyba"),
    WARNING("Varování"),
    INFO("Informace");

    private final String displayName;

    ValidationSeverity(String displayName) {
        this.displayName = displayName;
    }
}
