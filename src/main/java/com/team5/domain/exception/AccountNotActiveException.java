package com.team5.domain.exception;

public class AccountNotActiveException extends DomainException {

    public AccountNotActiveException() {
        super("ACC-403", "Account not active");
    }
}