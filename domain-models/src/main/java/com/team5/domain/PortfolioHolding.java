package com.team5.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class PortfolioHolding {

    @NotNull
    private final Long portfolioHoldingId;
    @NotNull
    private final Long portfolioId;
    @NotNull
    private final Long instrumentId;
    @NotNull
    @Positive
    private int quantity;
    @NotNull
    @PositiveOrZero
    private BigDecimal averageCost;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private static final MathContext AVG_COST_MC = new MathContext(20, RoundingMode.HALF_UP);

    public PortfolioHolding(Long portfolioHoldingId, Long portfolioId, Long instrumentId,
                            int quantity, BigDecimal averageCost,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.portfolioHoldingId = portfolioHoldingId;
        this.portfolioId        = portfolioId;
        this.instrumentId       = instrumentId;
        this.quantity           = quantity;
        this.averageCost        = averageCost != null ? averageCost.stripTrailingZeros() : null;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    public boolean hasEnough(@NotNull int requestedQuantity) {
        return quantity>=requestedQuantity;
    }

    public void buy(@Positive int units, @NotNull @Positive BigDecimal price) {
        BigDecimal oldValue  = BigDecimal.valueOf(quantity).multiply(averageCost, AVG_COST_MC);
        BigDecimal newValue  = BigDecimal.valueOf(units).multiply(price, AVG_COST_MC);
        int newQty    = quantity + units;

        averageCost = oldValue.add(newValue).divide(BigDecimal.valueOf(newQty), AVG_COST_MC);
        quantity    = newQty;
    }

    public void sell(@Positive int units) {
        if (!hasEnough(units)) {
            throw new IllegalStateException(
                "Cannot sell " + units
                + " units: holding is only " + quantity + " units.");
        }
        quantity = quantity - units;
    }

    public Long getPortfolioHoldingId() { return portfolioHoldingId; }
    public Long getPortfolioId()        { return portfolioId; }
    public Long getInstrumentId()       { return instrumentId; }
    public int getQuantity()     { return quantity; }
    public BigDecimal getAverageCost()  { return averageCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "PortfolioHolding{portfolioHoldingId=" + portfolioHoldingId
            + ", portfolioId=" + portfolioId + ", instrumentId=" + instrumentId
            + ", quantity=" + quantity + ", averageCost=" + averageCost + "}";
    }
}