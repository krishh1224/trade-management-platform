package com.team5.domain.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends TradeDomainException {

    private final BigDecimal balance;
    private final BigDecimal requested;

    public InsufficientFundsException(BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds: balance is " + balance.toPlainString()
            + " but debit of " + requested.toPlainString() + " was requested.");
        this.balance   = balance;
        this.requested = requested;
    }

    public BigDecimal getBalance()   { return balance; }
    public BigDecimal getRequested() { return requested; }
}