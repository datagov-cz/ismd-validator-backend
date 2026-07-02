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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Queries the published-vocabulary corpus (NKD) for the three uniqueness primitives the
 * global rules need: same-IRI, same-IRI-different-name, and different-IRI-same-name.
 *
 * <p>A concrete {@code @Component} that builds a plain {@link HttpSparqlExecutor}
 * in its constructor and guards every call with a {@link SparqlCircuitBreaker}. All
 * lookups fail open — a corpus outage (or an open breaker) returns empty and never throws
 * to the caller; see {@link #lenient} for how the breaker and fail-open compose.
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
     * DIFFERENT IRI, SAME NAME (batched): for the whole set of uploaded labels, find corpus
     * subjects (≠ the uploaded IRIs) typed {@code corpusType} carrying a label whose exact
     * lexical form and language tag match one of the uploaded {@code (text, lang)} pairs.
     *
     * <p>Issues a single SPARQL round-trip using {@code VALUES (?name ?lang) { ... }} instead
     * of one query per label — the older per-label form produced O(nodes × labels) sequential
     * calls on large uploads. Returns a map keyed by the matched {@link CandidateNode.Label},
     * whose values are the conflicting corpus IRIs (uploaded IRIs already filtered out).
     * An empty {@code labels} set issues no query.
     */
    public Map<CandidateNode.Label, List<String>> findOtherIrisByLabel(
            Set<CandidateNode.Label> labels, String corpusType, Set<String> excludeIris) {
        if (labels.isEmpty()) {
            return Map.of();
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.append("SELECT DISTINCT ?name ?lang ?other WHERE { VALUES (?name ?lang) { ");
        for (CandidateNode.Label label : labels) {
            pss.append("(");
            pss.appendLiteral(label.text());
            pss.append(" ");
            pss.appendLiteral(label.lang());
            pss.append(") ");
        }
        pss.append("} ?other a ");
        pss.appendIri(corpusType);
        pss.append(" ; ");
        pss.append(LABEL_PREDICATES);
        pss.append(" ?l . FILTER(STR(?l) = ?name && LANG(?l) = ?lang) }");

        return lenient("corpus diff-IRI same-name", pss.toString(), rs -> {
            Map<CandidateNode.Label, List<String>> out = new LinkedHashMap<>();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String name = SparqlSolutions.literalString(sol, "name");
                String lang = SparqlSolutions.literalString(sol, "lang");
                String other = SparqlSolutions.resourceUri(sol, "other");
                if (name == null || other == null || excludeIris.contains(other)) {
                    continue;
                }
                CandidateNode.Label key = new CandidateNode.Label(lang == null ? "" : lang, name);
                out.computeIfAbsent(key, k -> new ArrayList<>()).add(other);
            }
            return out;
        }, Map.of());
    }

    /**
     * Run a SELECT through the circuit breaker, failing open to {@code fallback}.
     *
     * <p>The breaker must guard the <em>strict</em> {@link HttpSparqlExecutor#select}, which
     * throws {@link com.dia.validation.sparql.SparqlEndpointUnavailableException} on an HTTP /
     * timeout failure — that throw is what lets the breaker count failures and open. If we let
     * the breaker guard a lenient executor call instead, every failure would already be
     * swallowed inside the supplier, the breaker would only ever see success, and it could
     * never open. The lenient behaviour is applied <em>here, outside</em> the breaker: both a
     * query failure and the breaker's own fast-fail (when open) are caught and degrade to
     * {@code fallback}, so corpus unavailability never propagates to validation.
     */
    private <T> T lenient(String op, String query, java.util.function.Function<ResultSet, T> mapper, T fallback) {
        if (!executor.isConfigured()) {
            return fallback;
        }
        try {
            return breaker.call(() -> executor.select(op, query, mapper));
        } catch (RuntimeException e) {
            log.warn("Corpus lookup '{}' skipped: {}", op, e.getMessage());
            return fallback;
        }
    }

    /** A corpus label binding: subject IRI, language tag ({@code ""} = untagged), lexical text. */
    public record IriLabel(String iri, String lang, String text) {
    }
}
