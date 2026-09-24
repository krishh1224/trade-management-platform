package com.neueda.learning.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Database record for Account entity.
 */
public class AccountRecord {
    private Long accId;
    private String accountNumber;
    private String riskProfile;
    private String baseCurrency;
    private BigDecimal balance;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AccountRecord() {
    }

    public AccountRecord(Long accId, String accountNumber, String riskProfile,
                         String baseCurrency, BigDecimal balance, String status, Long version,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.accId = accId;
        this.accountNumber = accountNumber;
        this.riskProfile = riskProfile;
        this.baseCurrency = baseCurrency;
        this.balance = balance;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public AccountRecord(Long accId, Long clientId, String accountNumber, String riskProfile,
                         String baseCurrency, BigDecimal balance, String status, Long version,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(accId, accountNumber, riskProfile, baseCurrency, balance, status, version, createdAt, updatedAt);
    }

    public Long getAccId() {
        return accId;
    }

    public void setAccId(Long accId) {
        this.accId = accId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getRiskProfile() {
        return riskProfile;
    }

    public void setRiskProfile(String riskProfile) {
        this.riskProfile = riskProfile;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "AccountRecord{" +
                "accId=" + accId +
                ", accountNumber='" + accountNumber + '\'' +
                ", riskProfile='" + riskProfile + '\'' +
                ", baseCurrency='" + baseCurrency + '\'' +
                ", balance=" + balance +
                ", status='" + status + '\'' +
                ", version=" + version +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

