package com.team5.domain;

import java.time.LocalDateTime;

public class Portfolio {

    private final Long portfolioId;
    private final String name;
    private final Long accountId;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Portfolio(Long portfolioId, String name, Long accountId,
                     String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (portfolioId == null)            throw new IllegalArgumentException("portfolioId must not be null.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Portfolio name must not be blank.");
        if (accountId == null)              throw new IllegalArgumentException("accountId must not be null.");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("Portfolio status must not be blank.");

        this.portfolioId = portfolioId;
        this.name        = name;
        this.accountId   = accountId;
        this.status      = status;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public Long getPortfolioId()     { return portfolioId; }
    public String getName()          { return name; }
    public Long getAccountId()       { return accountId; }
    public String getStatus()        { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Portfolio{portfolioId=" + portfolioId + ", name='" + name
            + "', accountId=" + accountId + ", status='" + status + "'}";
    }
}