package com.team5.exception;

public class DuplicateOrderException extends DomainException {

    public DuplicateOrderException() {
        super("ORD-409", "Duplicate order");
    }
}