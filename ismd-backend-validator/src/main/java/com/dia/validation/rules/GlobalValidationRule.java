package com.dia.validation.rules;

import com.dia.conversion.data.TransformationResult;
import com.dia.enums.ValidationSeverity;
import com.dia.exceptions.ValidationException;
import com.dia.validation.data.ValidationResult;

import java.util.List;

public interface GlobalValidationRule {

    String getRuleName();

    String getDescription();

    ValidationSeverity getDefaultSeverity();

    List<ValidationResult> validate(TransformationResult result) throws ValidationException;

    default boolean requiresNetworkAccess() {
        return true;
    }
}
