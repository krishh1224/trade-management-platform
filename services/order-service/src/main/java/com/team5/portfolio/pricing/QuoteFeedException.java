package com.team5.portfolio.pricing;

/** The market feed could not be reached or answered with something unusable. */
public class QuoteFeedException extends RuntimeException {

    public QuoteFeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
