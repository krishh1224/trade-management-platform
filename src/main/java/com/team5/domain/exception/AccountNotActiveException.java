package com.team5.domain.exception;

public class AccountNotActiveException extends TradeDomainException {

    private final Long accountId;

    public AccountNotActiveException(Long accountId) {
        super("Account " + accountId + " is not active and cannot perform this operation.");
        this.accountId = accountId;
    }

    public Long getAccountId() { return accountId; }
}