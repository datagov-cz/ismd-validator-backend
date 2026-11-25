package com.dia.validation.global;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-request record of corpus lookups that failed and were degraded to "no hits". Lets the engine
 * report "corpus unavailable" instead of an indistinguishable "no conflicts". Not shared across requests.
 */
public final class CorpusLookupTracker {

    /** For callers that do not care whether a lookup failed. */
    public static final CorpusLookupTracker IGNORE = new CorpusLookupTracker();

    private final Set<String> failedOperations = Collections.synchronizedSet(new LinkedHashSet<>());

    void failed(String operation) {
        if (this != IGNORE) {
            failedOperations.add(operation);
        }
    }

    public boolean anyFailed() {
        return !failedOperations.isEmpty();
    }

    public Set<String> failedOperations() {
        synchronized (failedOperations) {
            return Set.copyOf(failedOperations);
        }
    }
}
