package com.team5.domain.exception;

public class AccountNotFoundException extends DomainException {

    public AccountNotFoundException() {
        super("ACC-404", "Account not found");
    }
}