
package com.team5.domain.rules;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of idempotency keys that have already been used.
 */
public class InMemoryIdempotencyChecker implements IdempotencyChecker {

    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isDuplicate(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey must not be null.");
        }
        return seenKeys.contains(idempotencyKey);
    }

    @Override
    public void record(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey must not be null.");
        }
        seenKeys.add(idempotencyKey);
    }
}

