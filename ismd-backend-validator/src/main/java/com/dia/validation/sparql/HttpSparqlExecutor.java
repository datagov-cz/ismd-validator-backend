package com.dia.validation.sparql;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Executes SPARQL queries against an external HTTP endpoint (the published-vocabulary
 * corpus / NKD), applying an endpoint-empty pre-check, a configured per-call timeout,
 * and uniform exception handling via {@link SparqlExceptionMapper}.
 *
 * <p>Copied from {@code ismd-tool-backend}'s {@code HttpSparqlExecutor} (hard-rule:
 * mirror that module's external-SPARQL integration exactly). Plain Java class, not a
 * Spring bean — the corpus client injects its {@code @ConfigurationProperties} and
 * builds its executor in the constructor. There is intentionally <strong>no retry
 * loop</strong>; resilience is timeout + lenient fail-open + optional circuit breaker.
 */
@Slf4j
public final class HttpSparqlExecutor {

    private final String endpointLabel;
    private final String endpointUrl;
    private final int timeoutMs;

    /**
     * @param endpointLabel short human-readable name (e.g. {@code "NKD"}), surfaced in
     *                      log lines and exception messages.
     * @param endpointUrl   the SPARQL endpoint URL; may be blank/null (empty config), in
     *                      which case calls skip (lenient) or fail fast (strict).
     * @param timeoutMs     per-query timeout in milliseconds.
     */
    public HttpSparqlExecutor(String endpointLabel, String endpointUrl, int timeoutMs) {
        this.endpointLabel = endpointLabel;
        this.endpointUrl = endpointUrl;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Run a SELECT, mapping the {@link ResultSet} with {@code mapper}. The mapper is
     * invoked while the {@link QueryExecution} is still open, so streaming mappers are
     * safe. Strict mode: failures surface as {@link SparqlEndpointUnavailableException}.
     */
    public <T> T select(String operationLabel, String query, Function<ResultSet, T> mapper) {
        requireConfigured();
        return SparqlExceptionMapper.strict(
                operationLabel,
                SparqlEndpointUnavailableException.class,
                () -> {
                    try (QueryExecution qe = QueryExecutionHTTPBuilder.service(endpointUrl)
                            .query(query)
                            .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .build()) {
                        return mapper.apply(qe.execSelect());
                    }
                },
                (msg, cause) -> new SparqlEndpointUnavailableException(endpointLabel, msg, cause));
    }

    /**
     * Run a SELECT in lenient mode: unconfigured endpoint or any failure logs a warning
     * and returns {@code fallback}. This is the primary path for corpus lookups — a
     * corpus outage degrades a global check to "skipped" rather than failing validation.
     */
    public <T> T selectLenient(String operationLabel, String query, Function<ResultSet, T> mapper, T fallback) {
        if (!isConfigured()) {
            log.warn("{} endpoint not configured, skipping {}", endpointLabel, operationLabel);
            return fallback;
        }
        return SparqlExceptionMapper.lenient(
                operationLabel,
                () -> {
                    try (QueryExecution qe = QueryExecutionHTTPBuilder.service(endpointUrl)
                            .query(query)
                            .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .build()) {
                        return mapper.apply(qe.execSelect());
                    }
                },
                fallback);
    }

    /**
     * Run a CONSTRUCT and return the result {@link Model}. Empty/null result models are
     * returned as {@link Optional#empty()}. Strict mode.
     */
    public Optional<Model> construct(String operationLabel, String query) {
        requireConfigured();
        return SparqlExceptionMapper.strict(
                operationLabel,
                SparqlEndpointUnavailableException.class,
                () -> {
                    Model model = QueryExecutionHTTPBuilder.service(endpointUrl)
                            .query(query)
                            .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .construct();
                    return (model == null || model.isEmpty()) ? Optional.empty() : Optional.of(model);
                },
                (msg, cause) -> new SparqlEndpointUnavailableException(endpointLabel, msg, cause));
    }

    public boolean isConfigured() {
        return endpointUrl != null && !endpointUrl.trim().isEmpty();
    }

    public String endpointLabel() {
        return endpointLabel;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new SparqlEndpointUnavailableException(
                    endpointLabel, endpointLabel + " endpoint not configured");
        }
    }
}
