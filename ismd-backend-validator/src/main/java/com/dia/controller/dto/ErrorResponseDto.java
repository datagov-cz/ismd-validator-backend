package com.dia.controller.dto;

import com.dia.validation.ValidatorErrorResponse;

import java.time.Instant;

/**
 * Validator error body. Extends the shared {@link ValidatorErrorResponse} (in the common library)
 * so the calling tool can deserialize the same shape; this subclass keeps the existing
 * construction call sites in {@code GlobalExceptionHandler} unchanged.
 */
public class ErrorResponseDto extends ValidatorErrorResponse {
    public ErrorResponseDto(String error, String message, Instant timestamp, String requestId) {
        super(error, message, timestamp, requestId);
    }
}