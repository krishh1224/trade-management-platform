package com.neueda.learning.exception;

/**
 * Thrown for permanent (poison) message failures that should NOT be retried.
 * These messages are sent directly to the dead-letter topic on first occurrence.
 *
 * Examples:
 * - Malformed JSON
 * - Missing orderId field
 * - orderId not found in database
 * - Unexpected event type (for executor context)
 */
public class PermanentFailureException extends RuntimeException {
    private final String failureReason;

    public PermanentFailureException(String failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public PermanentFailureException(String failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
