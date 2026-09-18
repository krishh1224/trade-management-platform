package com.neueda.learning.exception;

/**
 * Thrown when Fauxnance quote cannot be obtained after retries.
 */
public class QuoteUnavailableException extends RuntimeException {
    public QuoteUnavailableException(String message) {
        super(message);
    }

    public QuoteUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

