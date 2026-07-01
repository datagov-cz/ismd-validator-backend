package com.dia.validation.sparql;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Literal;

/**
 * Null-safe accessors for Jena {@link QuerySolution} bindings, used by SPARQL SELECT
 * result mappers. Copied from {@code ismd-tool-backend}'s {@code SparqlSolutions} and
 * extended with {@link #literalLang} (label language tag) which the corpus uniqueness
 * lookups need for lang-scoped comparison.
 *
 * <p>Each helper returns {@code null} (or the documented default) when the variable is
 * unbound, has the wrong RDF node kind, or fails to parse.
 */
public final class SparqlSolutions {

    private SparqlSolutions() {
    }

    public static String resourceUri(QuerySolution sol, String var) {
        return (sol.contains(var) && sol.get(var).isResource()) ? sol.getResource(var).getURI() : null;
    }

    public static String literalString(QuerySolution sol, String var) {
        return (sol.contains(var) && sol.get(var).isLiteral()) ? sol.getLiteral(var).getString() : null;
    }

    /**
     * The language tag of a literal binding (e.g. {@code "cs"}), or {@code ""} for an
     * untagged literal, or {@code null} when unbound / not a literal. Untagged literals
     * are their own bucket for lang-scoped comparison (never match a tagged label).
     */
    public static String literalLang(QuerySolution sol, String var) {
        if (!sol.contains(var) || !sol.get(var).isLiteral()) {
            return null;
        }
        Literal lit = sol.getLiteral(var);
        String lang = lit.getLanguage();
        return lang == null ? "" : lang;
    }
}
