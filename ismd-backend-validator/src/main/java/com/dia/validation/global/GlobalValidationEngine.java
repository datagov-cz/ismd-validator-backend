package com.dia.validation.global;

import com.dia.validation.ValidationResult;
import com.dia.validation.ValidationSeverity;
import com.dia.validation.config.RuleManager;
import com.dia.validation.data.ISMDValidationReport;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the six cross-vocabulary global uniqueness rules against the published corpus,
 * app-side (SPARQL SELECTs via {@link CorpusSparqlClient}) rather than as in-memory SHACL.
 *
 * <p>Output mirrors {@code SHACLRuleEngine}: an {@link ISMDValidationReport} of
 * {@link ValidationResult}s, ready to merge into the combined report. Fail-open: when the
 * corpus endpoint is unset the engine returns an empty report; when it is set but the
 * client returns nothing because it was unreachable, the individual lookups have already
 * degraded to "no hits" (lenient), so validation still succeeds.
 *
 * <p>Concept candidates are extracted for subjects typed {@code slovníky:pojem} OR
 * {@code skos:Concept} (union on the upload side, so neither typing is missed); corpus
 * lookups then query the {@code slovníky:pojem} superset. Vocabulary candidates are
 * subjects typed {@code skos:ConceptScheme}.
 */
@Slf4j
@Component
public class GlobalValidationEngine {

    private static final String POJEM_TYPE =
            "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/pojem";
    private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
    private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String DCT = "http://purl.org/dc/terms/";

    private final CorpusSparqlClient client;
    private final RuleManager ruleManager;

    public GlobalValidationEngine(CorpusSparqlClient client, RuleManager ruleManager) {
        this.client = client;
        this.ruleManager = ruleManager;
    }

    public ISMDValidationReport validate(Model dataModel) {
        if (!client.isConfigured()) {
            log.debug("Corpus endpoint not configured — skipping global validation.");
            return ISMDValidationReport.empty();
        }

        Set<String> enabledGlobalRules = ruleManager.getEnabledGlobalRuleNames();
        if (enabledGlobalRules.isEmpty()) {
            return ISMDValidationReport.empty();
        }

        List<CandidateNode> concepts = extractCandidates(dataModel, conceptTypes(),
                new String[]{RDFS + "label", SKOS + "prefLabel"});
        List<CandidateNode> vocabularies = extractCandidates(dataModel, Set.of(SKOS + "ConceptScheme"),
                new String[]{RDFS + "label", SKOS + "prefLabel", DCT + "title"});

        List<ValidationResult> results = new ArrayList<>();
        for (String ruleName : enabledGlobalRules) {
            GlobalRuleKind kind = GlobalRuleKind.forRuleName(ruleName);
            if (kind == null) {
                log.warn("Enabled global rule '{}' has no registered kind — skipping.", ruleName);
                continue;
            }
            ruleManager.getRuleModel(ruleName)
                    .flatMap(GlobalRuleMetadata::from)
                    .ifPresentOrElse(
                            meta -> results.addAll(runRule(kind, meta,
                                    kind.target() == GlobalRuleKind.Target.CONCEPT ? concepts : vocabularies)),
                            () -> log.warn("Global rule '{}' has no parseable shape metadata — skipping.", ruleName));
        }

        return new ISMDValidationReport(results, Instant.now());
    }

    private Set<String> conceptTypes() {
        return Set.of(POJEM_TYPE, SKOS + "Concept");
    }

    private String corpusType(GlobalRuleKind.Target target) {
        return target == GlobalRuleKind.Target.CONCEPT
                ? CorpusSparqlClient.TYPE_POJEM
                : CorpusSparqlClient.TYPE_CONCEPT_SCHEME;
    }

    private List<ValidationResult> runRule(GlobalRuleKind kind, GlobalRuleMetadata meta,
                                           List<CandidateNode> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        String corpusType = corpusType(kind.target());
        return switch (kind.check()) {
            case SAME_IRI -> checkSameIri(meta, candidates, corpusType);
            case SAME_IRI_DIFF_NAME -> checkSameIriDiffName(meta, candidates, corpusType);
            case DIFF_IRI_SAME_NAME -> checkDiffIriSameName(meta, candidates, corpusType);
        };
    }

    private List<ValidationResult> checkSameIri(GlobalRuleMetadata meta, List<CandidateNode> candidates,
                                                String corpusType) {
        List<String> iris = candidates.stream().map(CandidateNode::iri).toList();
        Set<String> existing = client.findExistingIris(iris, corpusType);
        List<ValidationResult> out = new ArrayList<>();
        for (String iri : iris) {
            if (existing.contains(iri)) {
                out.add(result(meta, iri, null));
            }
        }
        return out;
    }

    private List<ValidationResult> checkSameIriDiffName(GlobalRuleMetadata meta, List<CandidateNode> candidates,
                                                        String corpusType) {
        List<String> iris = candidates.stream().map(CandidateNode::iri).toList();
        // corpus labels for the same IRIs, grouped by IRI then language
        Map<String, Map<String, Set<String>>> corpusByIriLang = new LinkedHashMap<>();
        for (CorpusSparqlClient.IriLabel il : client.fetchCorpusLabels(iris, corpusType)) {
            corpusByIriLang
                    .computeIfAbsent(il.iri(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(il.lang(), k -> new java.util.HashSet<>())
                    .add(il.text());
        }
        List<ValidationResult> out = new ArrayList<>();
        for (CandidateNode node : candidates) {
            Map<String, Set<String>> corpusLangs = corpusByIriLang.get(node.iri());
            if (corpusLangs == null) {
                continue; // IRI not published — that's the "same IRI" rule's job, not this one
            }
            for (CandidateNode.Label label : node.labels()) {
                Set<String> corpusTexts = corpusLangs.get(label.lang());
                // hit when the corpus publishes a same-language label that the upload doesn't carry
                if (corpusTexts != null && !corpusTexts.contains(label.text())) {
                    out.add(result(meta, node.iri(), String.join(" / ", corpusTexts)));
                    break; // one hit per node is enough
                }
            }
        }
        return out;
    }

    private List<ValidationResult> checkDiffIriSameName(GlobalRuleMetadata meta, List<CandidateNode> candidates,
                                                        String corpusType) {
        Set<String> uploadedIris = candidates.stream().map(CandidateNode::iri)
                .collect(java.util.stream.Collectors.toSet());

        // Collect the distinct labels across all candidates and resolve them in ONE batched
        // query, rather than one round-trip per (node, label).
        Set<CandidateNode.Label> allLabels = candidates.stream()
                .flatMap(n -> n.labels().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<CandidateNode.Label, List<String>> hitsByLabel =
                client.findOtherIrisByLabel(allLabels, corpusType, uploadedIris);
        if (hitsByLabel.isEmpty()) {
            return List.of();
        }

        List<ValidationResult> out = new ArrayList<>();
        for (CandidateNode node : candidates) {
            for (CandidateNode.Label label : node.labels()) {
                List<String> others = hitsByLabel.get(label);
                if (others != null && !others.isEmpty()) {
                    out.add(result(meta, node.iri(), others.get(0)));
                    break; // one hit per node is enough
                }
            }
        }
        return out;
    }

    private ValidationResult result(GlobalRuleMetadata meta, String focusIri, String value) {
        return new ValidationResult(
                meta.severity() != null ? meta.severity() : ValidationSeverity.INFO,
                meta.message(),
                meta.shapeIri(),
                focusIri,
                null,
                value);
    }

    /**
     * Extract candidate subjects of any of {@code types}, with their labels (each carrying a
     * language tag; {@code ""} for untagged literals). A subject typed under multiple of the
     * given types is emitted once.
     */
    private List<CandidateNode> extractCandidates(Model model, Set<String> types, String[] labelPredicates) {
        Map<String, List<CandidateNode.Label>> byIri = new LinkedHashMap<>();
        for (String type : types) {
            Resource typeRes = model.createResource(type);
            for (Statement st : model.listStatements(null, RDF.type, typeRes).toList()) {
                Resource subject = st.getSubject();
                if (!subject.isURIResource()) {
                    continue;
                }
                byIri.computeIfAbsent(subject.getURI(), k -> collectLabels(subject, labelPredicates));
            }
        }
        List<CandidateNode> out = new ArrayList<>();
        byIri.forEach((iri, labels) -> out.add(new CandidateNode(iri, labels)));
        return out;
    }

    private List<CandidateNode.Label> collectLabels(Resource subject, String[] labelPredicates) {
        List<CandidateNode.Label> labels = new ArrayList<>();
        Model model = subject.getModel();
        for (String predUri : labelPredicates) {
            Property pred = model.createProperty(predUri);
            for (Statement st : subject.listProperties(pred).toList()) {
                RDFNode o = st.getObject();
                if (o.isLiteral()) {
                    String lang = o.asLiteral().getLanguage();
                    labels.add(new CandidateNode.Label(lang == null ? "" : lang, o.asLiteral().getString()));
                }
            }
        }
        return labels;
    }
}
