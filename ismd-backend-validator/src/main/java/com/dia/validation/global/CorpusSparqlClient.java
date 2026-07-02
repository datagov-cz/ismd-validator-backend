package com.dia.validation.global;

import com.dia.validation.config.GlobalValidationConfiguration;
import com.dia.validation.sparql.HttpSparqlExecutor;
import com.dia.validation.sparql.SafeIri;
import com.dia.validation.sparql.SparqlCircuitBreaker;
import com.dia.validation.sparql.SparqlSolutions;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Queries the published-vocabulary corpus (NKD) for the three uniqueness primitives the
 * global rules need: same-IRI, same-IRI-different-name, and different-IRI-same-name.
 *
 * <p>Aconcrete {@code @Component} that builds a plain {@link HttpSparqlExecutor}
 * in its constructor and guards every call with a {@link SparqlCircuitBreaker}. All
 * lookups are lenient — a corpus outage returns empty (fail-open), never throws to the
 * caller (the breaker's fast-fail throw is swallowed here and treated as "no hits").
 *
 * <p>Corpus concept lookups query {@code slovníky:pojem}. Vocabulary lookups query
 * {@code skos:ConceptScheme}.
 */
@Slf4j
@Component
public class CorpusSparqlClient {

    public static final String CORPUS_LABEL = "NKD";

    /** Corpus type for concept uniqueness lookups — the superset type. */
    public static final String TYPE_POJEM =
            "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/pojem";
    /** Corpus type for vocabulary uniqueness lookups. */
    public static final String TYPE_CONCEPT_SCHEME =
            "http://www.w3.org/2004/02/skos/core#ConceptScheme";

    private static final String LABEL_PREDICATES =
            "<http://www.w3.org/2000/01/rdf-schema#label>"
                    + "|<http://www.w3.org/2004/02/skos/core#prefLabel>"
                    + "|<http://purl.org/dc/terms/title>";

    private final HttpSparqlExecutor executor;
    private final SparqlCircuitBreaker breaker;

    public CorpusSparqlClient(GlobalValidationConfiguration config) {
        this.executor = new HttpSparqlExecutor(
                CORPUS_LABEL,
                config.getSparql().getEndpoint(),
                config.getSparql().getTimeout());
        this.breaker = new SparqlCircuitBreaker(
                CORPUS_LABEL,
                config.getCircuitBreaker().getFailureThreshold(),
                config.getCircuitBreaker().getCooldownMs());
    }

    /** Whether the corpus endpoint is configured; when false, callers skip global checks. */
    public boolean isConfigured() {
        return executor.isConfigured();
    }

    /**
     * Same IRI: which of the given uploaded IRIs already exist in the corpus typed
     * {@code corpusType}. Returned set of input IRIs.
     */
    public Set<String> findExistingIris(List<String> uploadedIris, String corpusType) {
        List<String> safe = uploadedIris.stream().filter(SafeIri::isSafeHttpIri).toList();
        if (safe.isEmpty()) {
            return Set.of();
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.append("SELECT DISTINCT ?s WHERE { VALUES ?s { ");
        for (String iri : safe) {
            pss.appendIri(iri);
            pss.append(" ");
        }
        pss.append("} ?s a ");
        pss.appendIri(corpusType);
        pss.append(" }");

        return lenient("corpus same-IRI", pss.toString(), rs -> {
            Set<String> found = new HashSet<>();
            while (rs.hasNext()) {
                String s = SparqlSolutions.resourceUri(rs.next(), "s");
                if (s != null) {
                    found.add(s);
                }
            }
            return found;
        }, Set.of());
    }

    /**
     * Fetch all corpus labels (with language tags) for the given IRIs typed
     * {@code corpusType}. Used by the same-IRI-different-name check, which compares these
     * against the uploaded labels per-language in Java.
     */
    public List<IriLabel> fetchCorpusLabels(List<String> uploadedIris, String corpusType) {
        List<String> safe = uploadedIris.stream().filter(SafeIri::isSafeHttpIri).toList();
        if (safe.isEmpty()) {
            return List.of();
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.append("SELECT ?s ?l WHERE { VALUES ?s { ");
        for (String iri : safe) {
            pss.appendIri(iri);
            pss.append(" ");
        }
        pss.append("} ?s a ");
        pss.appendIri(corpusType);
        pss.append(" ; ");
        pss.append(LABEL_PREDICATES);
        pss.append(" ?l }");

        return lenient("corpus same-IRI labels", pss.toString(), rs -> {
            List<IriLabel> out = new ArrayList<>();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String s = SparqlSolutions.resourceUri(sol, "s");
                String text = SparqlSolutions.literalString(sol, "l");
                String lang = SparqlSolutions.literalLang(sol, "l");
                if (s != null && text != null) {
                    out.add(new IriLabel(s, lang, text));
                }
            }
            return out;
        }, List.of());
    }

    /**
     * DIFFERENT IRI, SAME NAME: corpus subjects (≠ the uploaded IRIs) typed
     * {@code corpusType} carrying a label whose exact lexical form and language tag match
     * {@code text}/{@code lang}. {@code excludeIris} are the uploaded IRIs to filter out.
     */
    public List<String> findOtherIrisWithLabel(String text, String lang, String corpusType,
                                               Set<String> excludeIris) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.append("SELECT DISTINCT ?other WHERE { ?other a ");
        pss.appendIri(corpusType);
        pss.append(" ; ");
        pss.append(LABEL_PREDICATES);
        pss.append(" ?l . FILTER(STR(?l) = ");
        pss.appendLiteral(text);
        if (lang != null && !lang.isEmpty()) {
            pss.append(" && LANG(?l) = ");
            pss.appendLiteral(lang);
        } else {
            pss.append(" && LANG(?l) = \"\"");
        }
        pss.append(") }");

        return lenient("corpus diff-IRI same-name", pss.toString(), rs -> {
            List<String> out = new ArrayList<>();
            while (rs.hasNext()) {
                String other = SparqlSolutions.resourceUri(rs.next(), "other");
                if (other != null && !excludeIris.contains(other)) {
                    out.add(other);
                }
            }
            return out;
        }, List.of());
    }

    /**
     * Run a lenient SELECT through the circuit breaker. The breaker's fast-fail throw
     * (when open) is swallowed and treated as an empty result, so corpus unavailability
     * never propagates to validation — global checks are simply skipped.
     */
    private <T> T lenient(String op, String query, java.util.function.Function<ResultSet, T> mapper, T fallback) {
        try {
            return breaker.call(() -> executor.selectLenient(op, query, mapper, fallback));
        } catch (RuntimeException e) {
            log.warn("Corpus lookup '{}' skipped: {}", op, e.getMessage());
            return fallback;
        }
    }

    /** A corpus label binding: subject IRI, language tag ({@code ""} = untagged), lexical text. */
    public record IriLabel(String iri, String lang, String text) {
    }
}
