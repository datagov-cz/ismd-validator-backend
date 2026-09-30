package com.dia.validation.sparql;

import org.apache.jena.irix.IRIException;
import org.apache.jena.irix.IRIx;

/**
 * IRI safety guard for SPARQL query construction. This is the endpoint-agnostic
 * {@code isSafeHttpIri} check lifted verbatim from {@code ismd-tool-backend}'s
 * {@code SparqlIriValidator} (the e-Sbírka registry parts of that class are not
 * relevant here, so only the guard is copied).
 *
 * <p>Every caller-supplied IRI must pass this before being interpolated into a
 * {@code <...>} reference or a {@code VALUES} block.
 */
public final class SafeIri {

    private SafeIri() {
    }

    /**
     * Validates that an IRI is safe to interpolate into a SPARQL {@code <...>}
     * reference. Rejects null/blank, relative IRIs, non-http(s) schemes, and any
     * character that could close the {@code <...>} reference and inject SPARQL.
     */
    public static boolean isSafeHttpIri(String iri) {
        if (iri == null || iri.isBlank()) {
            return false;
        }
        String scheme;
        try {
            IRIx parsed = IRIx.create(iri);
            if (!parsed.isReference()) {
                return false;
            }
            scheme = parsed.scheme();
        } catch (IRIException e) {
            return false;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        for (int i = 0; i < iri.length(); i++) {
            char c = iri.charAt(i);
            if (c <= 0x20 || c == '<' || c == '>' || c == '"' || c == '{' || c == '}'
                    || c == '|' || c == '^' || c == '`' || c == '\\') {
                return false;
            }
        }
        return true;
    }
}
