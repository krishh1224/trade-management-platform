package com.neueda.learning.model;

import java.time.LocalDateTime;

/**
 * Database record for Portfolio entity.
 */
public class PortfolioRecord {
    private Long portfolioId;
    private String name;
    private Long accountId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PortfolioRecord() {
    }

    public PortfolioRecord(Long portfolioId, String name, Long accountId, String status,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.portfolioId = portfolioId;
        this.name = name;
        this.accountId = accountId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        return "PortfolioRecord{" +
                "portfolioId=" + portfolioId +
                ", name='" + name + '\'' +
                ", accountId=" + accountId +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

