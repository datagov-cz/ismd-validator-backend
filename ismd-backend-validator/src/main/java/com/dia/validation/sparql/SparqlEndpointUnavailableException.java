package com.dia.validation.sparql;

import lombok.Getter;

/**
 * External SPARQL endpoint (the published-vocabulary corpus / NKD) is unreachable,
 * returning errors, or returning data we can't parse.
 *
 * <p>Mirrors {@code ismd-tool-backend}'s exception of the same name. In the validator
 * this is <em>not</em> mapped to an HTTP status: corpus lookups are an enhancement layer
 * and are always caught leniently (see {@link SparqlExceptionMapper#lenient}), so a
 * corpus outage degrades a global check to "skipped" rather than failing the request.
 * The type still exists because {@link HttpSparqlExecutor} and {@link SparqlCircuitBreaker}
 * key their behaviour off it.
 *
 * <p>{@code endpointLabel} identifies which upstream failed (e.g. {@code "NKD"}).
 */
@Getter
public class SparqlEndpointUnavailableException extends RuntimeException {

    private final String endpointLabel;

    public SparqlEndpointUnavailableException(String endpointLabel, String message) {
        super(message);
        this.endpointLabel = endpointLabel;
    }

    public SparqlEndpointUnavailableException(String endpointLabel, String message, Throwable cause) {
        super(message, cause);
        this.endpointLabel = endpointLabel;
    }
}
