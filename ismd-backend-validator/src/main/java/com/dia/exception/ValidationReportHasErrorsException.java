package com.dia.exception;

import com.dia.controller.dto.ConversionResponseDto;
import lombok.Getter;

/** The conversion succeeded but its validation found errors, so the output is withheld. Carries the results. */
@Getter
public class ValidationReportHasErrorsException extends RuntimeException {

    private final transient ConversionResponseDto results;

    public ValidationReportHasErrorsException(ConversionResponseDto results) {
        super("Validační zpráva obsahuje chyby.");
        this.results = results;
    }
}