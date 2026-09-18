package com.neueda.learning.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Database record for Portfolio Holding entity.
 * Represents the quantity and average cost of an instrument in a portfolio.
 */
public class PortfolioHoldingRecord {
    private Long portfolioHoldingId;
    private Long portfolioId;
    private Long instrumentId;
    private Long quantity;
    private BigDecimal avgCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PortfolioHoldingRecord() {
    }

    public PortfolioHoldingRecord(Long portfolioHoldingId, Long portfolioId, Long instrumentId,
                                  Long quantity, BigDecimal avgCost,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.portfolioHoldingId = portfolioHoldingId;
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.avgCost = avgCost;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getPortfolioHoldingId() {
        return portfolioHoldingId;
    }

    public void setPortfolioHoldingId(Long portfolioHoldingId) {
        this.portfolioHoldingId = portfolioHoldingId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
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
        return "PortfolioHoldingRecord{" +
                "portfolioHoldingId=" + portfolioHoldingId +
                ", portfolioId=" + portfolioId +
                ", instrumentId=" + instrumentId +
                ", quantity=" + quantity +
                ", avgCost=" + avgCost +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
