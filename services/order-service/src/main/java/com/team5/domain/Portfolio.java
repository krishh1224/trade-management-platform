package com.team5.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class Portfolio {

    @NotNull
    private final Long portfolioId;

    @NotBlank
    private final String name;

    @NotNull
    private final Long accountId;

    @NotBlank
    private final String status;

    private final LocalDateTime createdAt;

    private final LocalDateTime updatedAt;

    public Portfolio(Long portfolioId, String name, Long accountId,
                     String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
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