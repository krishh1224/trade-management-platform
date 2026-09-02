package com.team5.domain.exception;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException() {
        super("ORD-400", "Insufficient funds");
    }
}