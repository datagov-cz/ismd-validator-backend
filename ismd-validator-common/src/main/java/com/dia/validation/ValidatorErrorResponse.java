package com.dia.validation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Shared error body returned by the validator service on a 4xx/5xx. Lives in the common library
 * so the calling tool can deserialize it directly (rather than parsing the JSON loosely) and
 * surface the validator's own {@link #message} — e.g. "Invalid TTL syntax: …" — to the user.
 *
 * <p>{@code @NoArgsConstructor} + setters keep it Jackson-deserializable on the consumer side,
 * mirroring {@link ValidationReportDto}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ValidatorErrorResponse {

    /** Short error category, e.g. {@code "Validation Error"}. */
    private String error;

    /** Human-readable reason — the field the tool surfaces to the user. */
    private String message;

    private Instant timestamp;

    /** Correlation id (MDC request id) for cross-service log tracing. */
    private String requestId;

    public ValidatorErrorResponse(String error, String message, Instant timestamp, String requestId) {
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
        this.requestId = requestId;
    }
}