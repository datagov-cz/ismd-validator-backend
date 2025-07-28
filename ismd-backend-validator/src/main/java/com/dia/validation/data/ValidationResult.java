package com.dia.validation.data;

import com.dia.enums.ValidationSeverity;

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
        if (focusNodeUri == null) {
            return null;
        }

        int lastSlash = focusNodeUri.lastIndexOf('/');
        int lastHash = focusNodeUri.lastIndexOf('#');
        int splitIndex = Math.max(lastSlash, lastHash);

        if (splitIndex >= 0 && splitIndex < focusNodeUri.length() - 1) {
            return focusNodeUri.substring(splitIndex + 1);
        }

        return focusNodeUri;
    }

    public String getResultPathName() {
        if (resultPathUri == null) {
            return null;
        }

        int lastSlash = resultPathUri.lastIndexOf('/');
        int lastHash = resultPathUri.lastIndexOf('#');
        int splitIndex = Math.max(lastSlash, lastHash);

        if (splitIndex >= 0 && splitIndex < resultPathUri.length() - 1) {
            return resultPathUri.substring(splitIndex + 1);
        }

        return resultPathUri;
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
