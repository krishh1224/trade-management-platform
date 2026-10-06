package com.team5.domain.exception;

public class CashMovementConflictException extends DomainException {

    public CashMovementConflictException(String message) {
        super("CSH-409", message);
    }
}
