package com.team5.domain.exception;

public abstract class TradeDomainException extends RuntimeException {

    protected TradeDomainException(String message) {
        super(message);
    }

    protected TradeDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}