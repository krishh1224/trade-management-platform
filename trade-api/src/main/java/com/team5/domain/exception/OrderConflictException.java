package com.team5.domain.exception;

public class OrderConflictException extends DomainException {

    public OrderConflictException(String message) {
        super("ORD-409", message);
    }
}