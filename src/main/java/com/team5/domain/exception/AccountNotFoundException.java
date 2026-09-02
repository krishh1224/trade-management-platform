package com.team5.domain.exception;

public class AccountNotFoundException extends TradeDomainException {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("Account not found with id: " + accountId);
        this.accountId = accountId;
    }

    public Long getAccountId() { return accountId; }
}