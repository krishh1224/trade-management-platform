package com.team5.domain.rules;

/**
 * Port (seam) for the idempotency-key rule.
 *
 * In Sprint 6 the real authority for "have we seen this key before?" is the
 * unique constraint on {@code orders.idempotency_key} built in Sprint 3 - a
 * database-backed implementation of this interface will simply attempt an
 * insert and translate a constraint violation into "duplicate".
 *
 * For Sprint 5 there is no database, so the domain rule depends only on this
 * interface. Tests inject an in-memory implementation
 * ({@link InMemoryIdempotencyChecker}); nothing in {@link OrderValidator}
 * knows or cares how duplicates are actually tracked.
 */
public interface IdempotencyChecker {

    /**
     * @param idempotencyKey the client-supplied key for a prospective order
     * @return true if this key has already been recorded
     */
    boolean isDuplicate(String idempotencyKey);

    /**
     * Records a key as seen, so a later call with the same key is reported
     * as a duplicate. Recording is a separate step from validation: whether
     * and when a key gets recorded (e.g. only on successful placement, or
     * for rejected orders too) is an execution-layer decision, not a
     * validation-layer one.
     */
    void record(String idempotencyKey);
}
