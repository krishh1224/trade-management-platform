package com.team5.exception;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException() {
        super("ORD-400", "Insufficient funds");
    }
}