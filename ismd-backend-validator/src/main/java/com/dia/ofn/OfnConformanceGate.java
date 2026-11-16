package com.dia.ofn;

import com.dia.ofn.ConformanceIssue.Kind;
import com.dia.ofn.OfnContextIndex.Scope;
import com.dia.ofn.OfnContextIndex.Term;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Lets only OFN-conformant data through. Applied to the vocabulary document (in OFN context terms)
 * before any RDF is built, so non-conformant data is never transformed, not just left out of an export.
 * <ol>
 *   <li><b>Prune:</b> values with no term in the context, or with a shape the term does not allow
 *       (e.g. a non-IRI where the context says {@code @id}), are dropped. Shapes that only differ
 *       in wrapping (single value vs. {@code @set}) are normalised instead.</li>
 *   <li><b>Concepts:</b> each concept is checked against the schema variant of each of its types.
 *       A concept that fails is excluded.</li>
 *   <li><b>Cascade:</b> references to excluded concepts are removed from the remaining ones, which
 *       are then checked again (e.g. a Vlastnost whose {@code definiční-obor} was excluded).</li>
 *   <li><b>Vocabulary:</b> the vocabulary's own fields are checked against the schema.</li>
 * </ol>
 * Every rule comes from the context and schema in {@link OfnSpecification}; nothing here names an
 * OFN term, so a new OFN version needs no code change.
 */
@Slf4j
@Component
public class OfnConformanceGate {

    private static final String LANGUAGE_TAG = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]+)*";

    private final OfnSpecification specification;
    private final OfnContextIndex context;
    private final String idKey;
    private final String typeKey;

    public OfnConformanceGate(OfnSpecification specification) {
        this.specification = specification;
        this.context = specification.contextIndex();
        this.idKey = context.idAlias();
        this.typeKey = context.typeAlias();
    }

    public GateResult apply(ObjectNode vocabulary) {
        ObjectNode document = vocabulary.deepCopy();
        List<ConformanceIssue> issues = new ArrayList<>();

        pruneNode(document, rootScopes(), "", null, issues);
        filterConcepts(document, issues);
        checkVocabulary(document, issues);

        if (!issues.isEmpty()) {
            log.info("OFN conformance gate: {} value(s) dropped, {} concept(s) excluded, {} reference(s) removed",
                    count(issues, Kind.VALUE_DROPPED), count(issues, Kind.CONCEPT_EXCLUDED),
                    count(issues, Kind.REFERENCE_REMOVED));
        }
        return new GateResult(document, issues);
    }

    // ---------------------------------------------------------------- pruning

    private Deque<Scope> rootScopes() {
        Deque<Scope> scopes = new ArrayDeque<>();
        scopes.push(context.root());
        return scopes;
    }

    /**
     * Prunes one node object in place. {@code scopes} holds the active contexts, innermost first;
     * {@code conceptIri} is the concept the node belongs to (for reporting).
     */
    private void pruneNode(ObjectNode node, Deque<Scope> scopes, String location, String conceptIri,
                           List<ConformanceIssue> issues) {
        Deque<Scope> nodeScopes = pruneTypes(node, scopes, location, conceptIri, issues);

        for (String key : List.copyOf(names(node))) {
            String keyLocation = location.isEmpty() ? key : location + "/" + key;
            if (key.equals("@context") || key.equals(idKey) || key.equals(typeKey)) {
                continue; // @context is set by the builder; iri and typ are checked by the schema
            }
            Optional<Term> term = OfnContextIndex.resolve(nodeScopes, key);
            if (term.isEmpty()) {
                drop(node, key, keyLocation, conceptIri, issues);
                continue;
            }
            JsonNode pruned = pruneValue(term.get(), node.get(key), nodeScopes, keyLocation, conceptIri, issues);
            if (pruned == null || isEmptyContainer(pruned)) {
                node.remove(key);
            } else {
                node.set(key, pruned);
            }
        }
    }

    /** Drops types the context does not define and activates the type-scoped contexts of the rest. */
    private Deque<Scope> pruneTypes(ObjectNode node, Deque<Scope> scopes, String location, String conceptIri,
                                    List<ConformanceIssue> issues) {
        JsonNode types = node.get(typeKey);
        if (types == null) {
            return scopes;
        }
        Deque<Scope> result = new ArrayDeque<>(scopes);
        ArrayNode kept = JsonNodeFactory.instance.arrayNode();
        for (JsonNode type : asList(types)) {
            Optional<Term> typeTerm = type.isTextual()
                    ? OfnContextIndex.resolve(scopes, type.asText())
                    : Optional.empty();
            if (typeTerm.isEmpty() || typeTerm.get().isNotTypeTerm()) {
                issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location + "/" + typeKey,
                        "typ " + type + " není definován v kontextu OFN"));
                continue;
            }
            kept.add(type);
            if (typeTerm.get().nested() != null) {
                result.push(typeTerm.get().nested());
            }
        }
        node.set(typeKey, types.isArray() || kept.size() != 1 ? kept : kept.get(0));
        return result;
    }

    /** Returns the pruned value, or null when nothing of it is left. */
    private JsonNode pruneValue(Term term, JsonNode value, Deque<Scope> scopes, String location,
                                String conceptIri, List<ConformanceIssue> issues) {
        if ("@language".equals(term.container())) {
            return pruneLanguageMap(value, location, conceptIri, issues);
        }

        boolean isSet = "@set".equals(term.container()) || term.isReverse();
        if (!isSet && value.isArray()) {
            if (value.size() != 1) {
                issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location,
                        "pole povoluje jedinou hodnotu, nalezeno " + value.size()));
                return null;
            }
            value = value.get(0);
        }

        Deque<Scope> valueScopes = scopes;
        if (term.nested() != null) {
            valueScopes = new ArrayDeque<>(scopes);
            valueScopes.push(term.nested());
        }
        ArrayNode kept = JsonNodeFactory.instance.arrayNode();
        int index = 0;
        for (JsonNode item : asList(value)) {
            String itemLocation = isSet ? location + "[" + label(item, index) + "]" : location;
            index++;
            JsonNode prunedItem = pruneItem(term, item, valueScopes, itemLocation, conceptIri, issues);
            if (prunedItem != null) {
                kept.add(prunedItem);
            }
        }
        if (isSet) {
            return kept;
        }
        return kept.isEmpty() ? null : kept.get(0);
    }

    private JsonNode pruneItem(Term term, JsonNode item, Deque<Scope> scopes, String location,
                               String conceptIri, List<ConformanceIssue> issues) {
        String problem;
        if (item.isObject()) {
            if (term.nested() == null && !term.isReverse()) {
                problem = "pole nepovoluje vnořený objekt";
            } else {
                ObjectNode node = (ObjectNode) item;
                String owner = conceptIri != null ? conceptIri : node.path(idKey).asText(null);
                pruneNode(node, scopes, location, owner, issues);
                return node.isEmpty() ? null : node;
            }
        } else {
            problem = literalProblem(term, item);
        }
        if (problem == null) {
            return item;
        }
        issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location, problem + ": " + item));
        return null;
    }

    /** Why a non-object value does not fit the term, or null when it fits. */
    private String literalProblem(Term term, JsonNode item) {
        String type = term.type();
        if ("@id".equals(type)) {
            return item.isTextual() && context.isIriOrKnownCurie(item.asText()) ? null : "hodnota není IRI";
        }
        if ("xsd:boolean".equals(type)) {
            return item.isBoolean() ? null : "hodnota není logická hodnota (true/false)";
        }
        if (type != null && type.startsWith("xsd:")) {
            return item.isTextual() && !item.asText().isBlank() ? null : "hodnota není text (" + type + ")";
        }
        if (term.isReverse() || term.nested() != null) {
            return "pole očekává objekt";
        }
        return null;
    }

    private JsonNode pruneLanguageMap(JsonNode value, String location, String conceptIri,
                                      List<ConformanceIssue> issues) {
        if (!value.isObject()) {
            issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location,
                    "očekáván text s jazykem, nalezeno " + value));
            return null;
        }
        ObjectNode kept = JsonNodeFactory.instance.objectNode();
        value.properties().forEach(entry -> {
            String language = entry.getKey();
            if (!language.matches(LANGUAGE_TAG)) {
                issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location,
                        "'" + language + "' není jazykový kód"));
                return;
            }
            ArrayNode texts = JsonNodeFactory.instance.arrayNode();
            for (JsonNode text : asList(entry.getValue())) {
                if (text.isTextual() && !text.asText().isBlank()) {
                    texts.add(text);
                } else {
                    issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location + "/" + language,
                            "hodnota není neprázdný text: " + text));
                }
            }
            if (!texts.isEmpty()) {
                kept.set(language, entry.getValue().isArray() ? texts : texts.get(0));
            }
        });
        return kept;
    }

    // ---------------------------------------------------------------- concepts

    private void filterConcepts(ObjectNode document, List<ConformanceIssue> issues) {
        JsonNode concepts = document.get(OfnSpecification.CONCEPTS_KEY);
        if (concepts == null || !concepts.isArray()) {
            return;
        }
        List<ObjectNode> kept = new ArrayList<>();
        concepts.forEach(concept -> {
            if (concept.isObject()) {
                kept.add((ObjectNode) concept);
            }
        });

        Set<String> excluded = new HashSet<>();
        Deque<ObjectNode> toCheck = new ArrayDeque<>(kept);
        while (!toCheck.isEmpty()) {
            Set<String> newlyExcluded = new LinkedHashSet<>();
            for (ObjectNode concept : toCheck) {
                List<String> errors = schemaErrors(concept);
                if (!errors.isEmpty()) {
                    String iri = concept.path(idKey).asText(null);
                    newlyExcluded.add(iri);
                    issues.add(new ConformanceIssue(Kind.CONCEPT_EXCLUDED, iri, conceptLocation(concept),
                            "pojem nesplňuje OFN schéma: " + String.join("; ", errors)));
                }
            }
            kept.removeIf(concept -> newlyExcluded.contains(concept.path(idKey).asText(null)));
            excluded.addAll(newlyExcluded);

            toCheck.clear();
            if (!newlyExcluded.isEmpty()) {
                for (ObjectNode concept : kept) {
                    if (removeReferences(concept, newlyExcluded, issues)) {
                        toCheck.add(concept);
                    }
                }
            }
        }

        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        kept.forEach(result::add);
        document.set(OfnSpecification.CONCEPTS_KEY, result);
    }

    /** Schema errors of a concept: against the variant of each of its types, or any variant if none match. */
    private List<String> schemaErrors(ObjectNode concept) {
        List<JsonSchema> variants = new ArrayList<>();
        for (JsonNode type : asList(concept.path(typeKey))) {
            JsonSchema variant = specification.conceptSchemasByType().get(type.asText());
            if (variant != null) {
                variants.add(variant);
            }
        }
        if (variants.isEmpty()) {
            variants.add(specification.conceptSchema());
        }
        Set<String> errors = new TreeSet<>();
        for (JsonSchema variant : variants) {
            variant.validate(concept).stream().map(ValidationMessage::getMessage).forEach(errors::add);
        }
        return List.copyOf(errors);
    }

    /**
     * Removes IRI references to the given concepts, at any depth of the concept. Every {@code @id} term is
     * scanned; only values naming an excluded concept are removed, so references to anything else (agendas,
     * provisions, NKOD datasets) are never touched. Compact IRIs are expanded before comparing.
     * Returns true when anything was removed.
     */
    private boolean removeReferences(ObjectNode concept, Set<String> targets, List<ConformanceIssue> issues) {
        Set<String> expandedTargets = new HashSet<>();
        for (String target : targets) {
            if (target != null) {
                expandedTargets.add(context.expandIri(target));
            }
        }
        return removeReferencesIn(concept, conceptScopes(concept), conceptLocation(concept),
                concept.path(idKey).asText(null), expandedTargets, issues);
    }

    private boolean removeReferencesIn(ObjectNode node, Deque<Scope> scopes, String location, String conceptIri,
                                       Set<String> targets, List<ConformanceIssue> issues) {
        boolean changed = false;
        for (String key : List.copyOf(names(node))) {
            if (key.equals("@context") || key.equals(idKey) || key.equals(typeKey)) {
                continue;
            }
            Optional<Term> term = OfnContextIndex.resolve(scopes, key);
            if (term.isEmpty()) {
                continue;
            }
            Deque<Scope> valueScopes = scopes;
            if (term.get().nested() != null) {
                valueScopes = new ArrayDeque<>(scopes);
                valueScopes.push(term.get().nested());
            }
            boolean isReference = "@id".equals(term.get().type());
            boolean isSet = "@set".equals(term.get().container());
            String keyLocation = location + "/" + key;

            JsonNode value = node.get(key);
            List<JsonNode> items = asList(value);
            ArrayNode kept = JsonNodeFactory.instance.arrayNode();
            int index = 0;
            for (JsonNode item : items) {
                String itemLocation = isSet ? keyLocation + "[" + label(item, index) + "]" : keyLocation;
                index++;
                if (item.isObject()) {
                    ObjectNode nested = (ObjectNode) item;
                    changed |= removeReferencesIn(nested, withTypeScopes(valueScopes, nested), itemLocation,
                            conceptIri, targets, issues);
                    if (!nested.isEmpty()) {
                        kept.add(nested);
                    }
                } else if (isReference && item.isTextual() && targets.contains(context.expandIri(item.asText()))) {
                    issues.add(new ConformanceIssue(Kind.REFERENCE_REMOVED, conceptIri, keyLocation,
                            "odkaz na vyřazený pojem " + item.asText()));
                    changed = true;
                } else {
                    kept.add(item);
                }
            }
            if (kept.isEmpty()) {
                node.remove(key);
            } else if (kept.size() != items.size()) {
                node.set(key, value.isArray() ? kept : kept.get(0));
            }
        }
        return changed;
    }

    private Deque<Scope> conceptScopes(ObjectNode concept) {
        Deque<Scope> scopes = rootScopes();
        Term conceptsTerm = context.root().terms.get(OfnSpecification.CONCEPTS_KEY);
        if (conceptsTerm != null && conceptsTerm.nested() != null) {
            scopes.push(conceptsTerm.nested());
        }
        return withTypeScopes(scopes, concept);
    }

    /** The scopes with the type-scoped contexts of the node's types added. */
    private Deque<Scope> withTypeScopes(Deque<Scope> scopes, ObjectNode node) {
        Deque<Scope> result = new ArrayDeque<>(scopes);
        for (JsonNode type : asList(node.path(typeKey))) {
            OfnContextIndex.resolve(scopes, type.asText())
                    .map(Term::nested)
                    .ifPresent(result::push);
        }
        return result;
    }

    // ---------------------------------------------------------------- vocabulary

    private void checkVocabulary(ObjectNode document, List<ConformanceIssue> issues) {
        ObjectNode vocabularyOnly = document.deepCopy();
        vocabularyOnly.set(OfnSpecification.CONCEPTS_KEY, JsonNodeFactory.instance.arrayNode());
        specification.schema().validate(vocabularyOnly).stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .forEach(message -> issues.add(
                        new ConformanceIssue(Kind.VOCABULARY_INVALID, null, "", message)));
    }

    // ---------------------------------------------------------------- helpers

    private void drop(ObjectNode node, String key, String location, String conceptIri,
                      List<ConformanceIssue> issues) {
        issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, conceptIri, location, "pole není definováno v kontextu OFN"));
        node.remove(key);
    }

    private String conceptLocation(ObjectNode concept) {
        return OfnSpecification.CONCEPTS_KEY + "[" + label(concept, 0) + "]";
    }

    /** Last IRI segment of a node (e.g. "řidič"), or its array index when it has no IRI. */
    private String label(JsonNode item, int index) {
        String iri = item.path(idKey).asText("");
        return iri.isEmpty() ? String.valueOf(index) : iri.substring(iri.lastIndexOf('/') + 1);
    }

    private static boolean isEmptyContainer(JsonNode value) {
        return value.isContainerNode() && value.isEmpty();
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<JsonNode> asList(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            return List.of(value);
        }
        List<JsonNode> items = new ArrayList<>();
        value.forEach(items::add);
        return items;
    }

    private static long count(List<ConformanceIssue> issues, Kind kind) {
        return issues.stream().filter(issue -> issue.kind() == kind).count();
    }
}
