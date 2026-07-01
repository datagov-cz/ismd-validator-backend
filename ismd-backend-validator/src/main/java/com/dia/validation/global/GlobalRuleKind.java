package com.dia.validation.global;

import java.util.Map;

/**
 * The six global (corpus) rules, each mapped to the candidate kind it targets (concept vs
 * vocabulary) and the comparison primitive it performs. Dispatch is by the exact loaded
 * rule name (filename minus {@code .ttl}, lower-cased) — the {@code global-}/{@code local-}
 * prefix and the accented Czech names survive {@code sanitizeForIRI} unchanged.
 */
public enum GlobalRuleKind {

    CONCEPT_SAME_IRI(Target.CONCEPT, Check.SAME_IRI),
    CONCEPT_SAME_IRI_DIFF_NAME(Target.CONCEPT, Check.SAME_IRI_DIFF_NAME),
    CONCEPT_DIFF_IRI_SAME_NAME(Target.CONCEPT, Check.DIFF_IRI_SAME_NAME),
    VOCAB_SAME_IRI(Target.VOCAB, Check.SAME_IRI),
    VOCAB_SAME_IRI_DIFF_NAME(Target.VOCAB, Check.SAME_IRI_DIFF_NAME),
    VOCAB_DIFF_IRI_SAME_NAME(Target.VOCAB, Check.DIFF_IRI_SAME_NAME);

    public enum Target {CONCEPT, VOCAB}

    public enum Check {SAME_IRI, SAME_IRI_DIFF_NAME, DIFF_IRI_SAME_NAME}

    private final Target target;
    private final Check check;

    GlobalRuleKind(Target target, Check check) {
        this.target = target;
        this.check = check;
    }

    public Target target() {
        return target;
    }

    public Check check() {
        return check;
    }

    private static final Map<String, GlobalRuleKind> BY_RULE_NAME = Map.of(
            "global-pojem-se-stejným-iri", CONCEPT_SAME_IRI,
            "global-pojem-se-stejným-iri-jiným-názvem", CONCEPT_SAME_IRI_DIFF_NAME,
            "global-pojem-s-jiným-iri-stejným-názvem", CONCEPT_DIFF_IRI_SAME_NAME,
            "global-slovník-se-stejným-iri", VOCAB_SAME_IRI,
            "global-slovník-se-stejným-iri-jiným-názvem", VOCAB_SAME_IRI_DIFF_NAME,
            "global-slovník-s-jiným-iri-stejným-názvem", VOCAB_DIFF_IRI_SAME_NAME
    );

    /** The kind for a loaded rule name, or {@code null} if the name isn't a recognised global rule. */
    public static GlobalRuleKind forRuleName(String ruleName) {
        return ruleName == null ? null : BY_RULE_NAME.get(ruleName);
    }
}
