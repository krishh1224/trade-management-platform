package com.team5.domain;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.RiskProfile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class Account {

    @NotNull
    private final Long accId;
    
    @NotNull
    private final Long clientId;
    
    @NotBlank
    @Size(min = 10, max = 10)
    private final String accountNumber;
    
    @NotNull
    private final RiskProfile riskProfile;
    
    @NotBlank
    private final String baseCurrency;
    
    @NotNull
    @PositiveOrZero
    private BigDecimal balance;
    
    @NotNull
    private AccountStatus status;
    
    private final LocalDateTime createdAt;
    
    private final LocalDateTime updatedAt;

    public Account(Long accId, Long clientId, String accountNumber,
                   RiskProfile riskProfile, String baseCurrency, BigDecimal balance,
                   AccountStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {

        this.accId         = accId;
        this.clientId      = clientId;
        this.accountNumber = accountNumber;
        this.riskProfile   = riskProfile;
        this.baseCurrency  = baseCurrency;
        this.balance       = balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : null;
        this.status        = status;
        this.createdAt     = createdAt;
        this.updatedAt     = updatedAt;
    }

    public boolean canAfford(@NotNull BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(@NotNull @Positive BigDecimal amount) {
        if (!canAfford(amount)) {
            throw new IllegalStateException(
                "Insufficient funds: balance is " + balance.toPlainString()
                + " but debit of " + amount.toPlainString() + " was requested.");
        }
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void credit(@NotNull @Positive BigDecimal amount) {
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

    @Override
    public String toString() {
        return "Account{accId=" + accId + ", accountNumber='" + accountNumber
            + "', clientId=" + clientId + ", status=" + status + ", balance=" + balance + "}";
    }
}