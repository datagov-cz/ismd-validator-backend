package com.dia.validation.sparql;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Centralizes the SPARQL exception cascade. Mirrors {@code ismd-tool-backend}'s
 * {@code SparqlExceptionMapper}. Two failure modes:
 * <ul>
 *   <li>{@link #strict(String, Class, Supplier, BiFunction)} — wraps Jena's HTTP /
 *       generic exceptions into a domain exception. Kept for parity with the source
 *       pattern; the validator's corpus lookups use the lenient mode.</li>
 *   <li>{@link #lenient(String, Supplier, Object)} — logs a warning and returns the
 *       supplied fallback. Used for corpus lookups where a failed query must not break
 *       validation (fail-open enhancement layer).</li>
 * </ul>
 */
@Slf4j
public final class SparqlExceptionMapper {

    private SparqlExceptionMapper() {
    }

    /**
     * Run {@code action}; on Jena HTTP failure or any other exception, wrap with
     * {@code wrapper.apply(message, cause)} and rethrow. Domain exceptions of type
     * {@code domainExceptionType} thrown by the action itself propagate untouched
     * (avoids double-wrapping the not-configured pre-check).
     */
    public static <T, E extends RuntimeException> T strict(
            String label,
            Class<E> domainExceptionType,
            Supplier<T> action,
            BiFunction<String, Throwable, E> wrapper) {
        try {
            return action.get();
        } catch (QueryExceptionHTTP | HttpException e) {
            throw wrapper.apply(label + " fetch failed: " + e.getMessage(), e);
        } catch (Exception e) {
            if (domainExceptionType.isInstance(e)) {
                throw domainExceptionType.cast(e);
            }
            throw wrapper.apply(label + " mapping failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run {@code action}; on any exception log a warning and return {@code fallback}.
     * For best-effort operations where a failure shouldn't break the surrounding flow.
     */
    public static <T> T lenient(String label, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (QueryExceptionHTTP e) {
            log.warn("SPARQL error during {}: {}", label, e.getMessage());
            return fallback;
        } catch (HttpException e) {
            log.warn("HTTP error during {}: {}", label, e.getMessage());
            return fallback;
        } catch (Exception e) {
            log.warn("Unexpected error during {}: {}", label, e.getMessage());
            return fallback;
        }
    }
}
