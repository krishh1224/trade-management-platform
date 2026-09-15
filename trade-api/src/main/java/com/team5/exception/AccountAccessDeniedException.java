package com.team5.exception;

// The contract reports a token/account mismatch with the same code and message as an inactive account.
public class AccountAccessDeniedException extends DomainException {

    public AccountAccessDeniedException() {
        super("ACC-403", "Account not active");
    }
}
