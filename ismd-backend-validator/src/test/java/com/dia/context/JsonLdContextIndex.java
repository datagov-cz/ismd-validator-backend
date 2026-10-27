package com.dia.context;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * Read-only view of a JSON-LD context: prefixes, term definitions and their nested
 * (property-scoped or type-scoped) contexts. Every term is addressed by its path,
 * e.g. {@code pojmy/definující-nelegislativní-zdroj/Digitální objekt/url}.
 * <p>
 * Nothing here knows OFN term names, so a modified context needs no code change.
 */
final class JsonLdContextIndex {

    /** One term definition and the context it lives in. */
    record Term(String path, String name, JsonNode definition, Scope scope) {

        String id() {
            return definition.isTextual() ? definition.asText() : definition.path("@id").asText(null);
        }

        String type() {
            return definition.path("@type").asText(null);
        }

        String container() {
            return definition.path("@container").asText(null);
        }

        boolean isReverse() {
            return definition.has("@reverse");
        }

        /** Nested context of this term, or null. */
        Scope nested() {
            return scope.children.get(name);
        }

        /** By convention of the OFN contexts, capitalised terms name types (classes). */
        boolean isTypeTerm() {
            return !name.isEmpty() && Character.isUpperCase(name.codePointAt(0));
        }
    }

    /** One level of context: its term definitions and the nested contexts they open. */
    static final class Scope {
        final String path;
        final Map<String, Term> terms = new LinkedHashMap<>();
        final Map<String, Scope> children = new HashMap<>();

        Scope(String path) {
            this.path = path;
        }
    }

    private final Scope root = new Scope("");
    private final Map<String, String> prefixes = new LinkedHashMap<>();
    private final Map<String, Term> allTerms = new LinkedHashMap<>();

    JsonLdContextIndex(JsonNode contextDocument) {
        JsonNode context = contextDocument.path("@context");
        if (!context.isObject()) {
            throw new IllegalArgumentException("Context document has no @context object");
        }
        index(context, root);
    }

    private void index(JsonNode context, Scope scope) {
        context.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key.startsWith("@")) {
                return;
            }
            if (scope == root && isPrefixDefinition(value)) {
                prefixes.put(key, value.asText());
                return;
            }
            String path = scope.path.isEmpty() ? key : scope.path + "/" + key;
            Term term = new Term(path, key, value, scope);
            scope.terms.put(key, term);
            allTerms.put(path, term);
            if (value.isObject() && value.path("@context").isObject()) {
                Scope child = new Scope(path);
                scope.children.put(key, child);
                index(value.get("@context"), child);
            }
        });
    }

    private static boolean isPrefixDefinition(JsonNode value) {
        if (!value.isTextual()) {
            return false;
        }
        String iri = value.asText();
        return iri.endsWith("/") || iri.endsWith("#");
    }

    Scope root() {
        return root;
    }

    Map<String, String> prefixes() {
        return Collections.unmodifiableMap(prefixes);
    }

    /** All term definitions by path, in context order. */
    Map<String, Term> allTerms() {
        return Collections.unmodifiableMap(allTerms);
    }

    /** Resolves a term against the active scopes, innermost first. */
    static Optional<Term> resolve(Deque<Scope> activeScopes, String name) {
        for (Scope scope : activeScopes) {
            Term term = scope.terms.get(name);
            if (term != null) {
                return Optional.of(term);
            }
        }
        return Optional.empty();
    }

    /** True for an absolute http(s) IRI or a compact IRI whose prefix the context defines. */
    boolean isIriOrKnownCurie(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return !value.contains(" ");
        }
        int colon = value.indexOf(':');
        return colon > 0 && prefixes.containsKey(value.substring(0, colon)) && !value.contains(" ");
    }
}
