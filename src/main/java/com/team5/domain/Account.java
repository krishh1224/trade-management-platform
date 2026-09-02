package com.team5.domain;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.RiskProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class Account {

    private final Long accId;
    private final Long clientId;
    private final String accountNumber;
    private final RiskProfile riskProfile;
    private final String baseCurrency;
    private BigDecimal balance;
    private AccountStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Account(Long accId, Long clientId, String accountNumber,
                   RiskProfile riskProfile, String baseCurrency, BigDecimal balance,
                   AccountStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (accId == null)                          throw new IllegalArgumentException("accId must not be null.");
        if (clientId == null)                       throw new IllegalArgumentException("clientId must not be null.");
        if (accountNumber == null || accountNumber.isBlank()) throw new IllegalArgumentException("accountNumber must not be blank.");
        if (riskProfile == null)                    throw new IllegalArgumentException("riskProfile must not be null.");
        if (baseCurrency == null || baseCurrency.isBlank())   throw new IllegalArgumentException("baseCurrency must not be blank.");
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("balance must be non-negative.");
        if (status == null)                         throw new IllegalArgumentException("status must not be null.");

        this.accId         = accId;
        this.clientId      = clientId;
        this.accountNumber = accountNumber;
        this.riskProfile   = riskProfile;
        this.baseCurrency  = baseCurrency;
        this.balance       = balance.setScale(2, RoundingMode.HALF_UP);
        this.status        = status;
        this.createdAt     = createdAt;
        this.updatedAt     = updatedAt;
    }

    public boolean canAfford(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("amount must not be null.");
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        validatePositive(amount, "debit");
        if (!canAfford(amount)) {
            throw new IllegalStateException(
                "Insufficient funds: balance is " + balance.toPlainString()
                + " but debit of " + amount.toPlainString() + " was requested.");
        }
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void credit(BigDecimal amount) {
        validatePositive(amount, "credit");
        balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(status);
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    public Long getAccId()             { return accId; }
    public Long getClientId()          { return clientId; }
    public String getAccountNumber()   { return accountNumber; }
    public RiskProfile getRiskProfile(){ return riskProfile; }
    public String getBaseCurrency()    { return baseCurrency; }
    public BigDecimal getBalance()     { return balance; }
    public AccountStatus getStatus()   { return status; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }

    private static void validatePositive(BigDecimal amount, String op) {
        if (amount == null)
            throw new IllegalArgumentException("Amount for " + op + " must not be null.");
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount for " + op + " must be positive; got " + amount.toPlainString());
    }

    @Override
    public String toString() {
        return "Account{accId=" + accId + ", accountNumber='" + accountNumber
            + "', clientId=" + clientId + ", status=" + status + ", balance=" + balance + "}";
    }
}