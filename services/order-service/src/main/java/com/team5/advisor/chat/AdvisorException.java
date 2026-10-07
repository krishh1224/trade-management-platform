package com.team5.advisor.chat;

import org.springframework.http.HttpStatus;

/** A failure with a contract error code (advisor-api.yaml ErrorResponse) and its HTTP status. */
public class AdvisorException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public AdvisorException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static AdvisorException invalidInput() {
        return new AdvisorException("VAL-422", HttpStatus.UNPROCESSABLE_ENTITY, "Invalid input");
    }

    public static AdvisorException accountNotFound() {
        return new AdvisorException("ACC-404", HttpStatus.NOT_FOUND, "Account not found");
    }

    public static AdvisorException accountNotActive() {
        return new AdvisorException("ACC-403", HttpStatus.FORBIDDEN, "Account not active");
    }

    public static AdvisorException rateLimited() {
        return new AdvisorException("ADV-429", HttpStatus.TOO_MANY_REQUESTS,
            "The advisor is busy. Please try again in a minute.");
    }

    public static AdvisorException unavailable() {
        return new AdvisorException("ADV-503", HttpStatus.SERVICE_UNAVAILABLE,
            "The advisor is unavailable right now. Please try again later.");
    }

    /**
     * Maps a failure from the AI provider to ADV-429 when it is a rate limit (HTTP 429 /
     * RESOURCE_EXHAUSTED anywhere in the cause chain) and ADV-503 otherwise.
     */
    public static AdvisorException fromProviderFailure(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            String message = String.valueOf(t.getMessage());
            if (message.contains("429") || message.contains("RESOURCE_EXHAUSTED")
                || t.getClass().getSimpleName().contains("RateLimit")) {
                return rateLimited();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return unavailable();
    }
}
