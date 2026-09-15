package com.team5.exception;

// The contract pairs HTTP 404 with the ORD-409 code here, so ApiExceptionHandler maps this type explicitly.
public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException() {
        super("ORD-409", "Order not found");
    }
}
