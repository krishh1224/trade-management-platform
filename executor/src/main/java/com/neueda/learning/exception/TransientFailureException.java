package com.neueda.learning.exception;

/**
 * Thrown for transient (recoverable) message processing failures.
 * These messages are retried with bounded exponential backoff before DLT routing.
 *
 * Examples:
 * - Brief Kafka broker unavailability
 * - Temporary database connection loss
 * - Settlement optimistic-lock conflict after max retries exhausted
 * - Temporary network issues
 */
public class TransientFailureException extends RuntimeException {
    private final String failureReason;
    private final boolean retriesExhausted;

    public TransientFailureException(String failureReason, String message) {
        this(failureReason, message, false);
    }

    public TransientFailureException(String failureReason, String message, boolean retriesExhausted) {
        super(message);
        this.failureReason = failureReason;
        this.retriesExhausted = retriesExhausted;
    }

    public TransientFailureException(String failureReason, String message, Throwable cause) {
        this(failureReason, message, false, cause);
    }

    public TransientFailureException(String failureReason, String message, boolean retriesExhausted, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
        this.retriesExhausted = retriesExhausted;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean isRetriesExhausted() {
        return retriesExhausted;
    }
}
