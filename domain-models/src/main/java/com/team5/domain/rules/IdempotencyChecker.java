
package com.team5.domain.rules;

// Checks whether an idempotency key has already been used.
 
public interface IdempotencyChecker {

    boolean isDuplicate(String idempotencyKey);

    void record(String idempotencyKey);
}
