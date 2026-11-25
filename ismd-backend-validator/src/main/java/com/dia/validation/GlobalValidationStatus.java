package com.dia.validation;

/**
 * Whether the corpus-aware (global) checks ran. Without it a client cannot tell "no conflicts with the
 * corpus" from "the corpus was never checked".
 */
public enum GlobalValidationStatus {
    /** Every corpus lookup succeeded; absence of global results means no conflicts. */
    OK,
    /** Not attempted: no corpus endpoint configured, or no global rule enabled. */
    SKIPPED,
    /** Attempted, but at least one corpus lookup failed (timeout, HTTP error, open circuit breaker). */
    UNAVAILABLE
}
