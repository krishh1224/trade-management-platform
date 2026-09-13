package com.team5.exception;

public class AccountNotFoundException extends DomainException {

    public AccountNotFoundException() {
        super("ACC-404", "Account not found");
    }
}