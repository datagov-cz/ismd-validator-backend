package com.dia.validation;

import com.dia.utility.UtilityMethods;

public record ValidationResult(ValidationSeverity severity, String message, String ruleName, String focusNodeUri,
                               String resultPathUri, String value) {

    public boolean isError() {
        return severity == ValidationSeverity.ERROR;
    }

    public boolean isWarning() {
        return severity == ValidationSeverity.WARNING;
    }

    public boolean isInfo() {
        return severity == ValidationSeverity.INFO;
    }

    public String getFocusNodeName() {
        return UtilityMethods.extractNameFromIRI(focusNodeUri);
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationResult{severity=%s, rule='%s', message='%s', focusNode='%s'}",
                severity,
                ruleName,
                message,
                getFocusNodeName()
        );
    }
}