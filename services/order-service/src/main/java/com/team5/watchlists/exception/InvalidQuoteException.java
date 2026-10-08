package com.team5.watchlists.exception;

// A market-data message that can never be evaluated (malformed JSON, no symbol, no usable price, no
// time). Not retryable: the consumer sends it to market-data.DLT with this reason in a header.
public class InvalidQuoteException extends RuntimeException {

    private final String failureReason;

    public InvalidQuoteException(String failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public InvalidQuoteException(String failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
