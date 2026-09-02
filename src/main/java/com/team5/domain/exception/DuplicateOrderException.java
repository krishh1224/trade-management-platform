package com.team5.domain.exception;

public class DuplicateOrderException extends TradeDomainException {

    private final String idempotencyKey;

    public DuplicateOrderException(String idempotencyKey) {
        super("Duplicate order: idempotency key '" + idempotencyKey + "' has already been used.");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}