package com.team5.strategies.exception;

// A market-data message that can never be evaluated (no symbol, no price, no time, bad JSON). Not
// retried: it goes to market-data.DLT with the reason in the x-failure-reason header.
public class UnusableQuoteException extends RuntimeException {

    private final String failureReason;

    public UnusableQuoteException(String failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public UnusableQuoteException(String failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
