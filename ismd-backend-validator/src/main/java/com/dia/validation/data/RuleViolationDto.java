package com.dia.validation.data;

public record RuleViolationDto(String name, String description, String level, String severity, String message,
                               String value) {
}
