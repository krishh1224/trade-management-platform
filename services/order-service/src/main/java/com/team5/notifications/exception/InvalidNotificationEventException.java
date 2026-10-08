package com.team5.notifications.exception;

// A trade-events message or alert that can never become a notification (malformed JSON,
// missing eventId, missing account). Not retryable: the consumer sends it to trade-events.DLT.
public class InvalidNotificationEventException extends RuntimeException {

    private final String failureReason;

    public InvalidNotificationEventException(String failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public InvalidNotificationEventException(String failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
