package com.team5.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class PortfolioHolding {

    private final Long portfolioHoldingId;
    private final Long portfolioId;
    private final Long instrumentId;
    private BigDecimal quantity;
    private BigDecimal averageCost;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private static final MathContext AVG_COST_MC = new MathContext(20, RoundingMode.HALF_UP);

    public PortfolioHolding(Long portfolioHoldingId, Long portfolioId, Long instrumentId,
                            BigDecimal quantity, BigDecimal averageCost,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (portfolioHoldingId == null) throw new IllegalArgumentException("portfolioHoldingId must not be null.");
        if (portfolioId == null)        throw new IllegalArgumentException("portfolioId must not be null.");
        if (instrumentId == null)       throw new IllegalArgumentException("instrumentId must not be null.");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0)     throw new IllegalArgumentException("quantity must not be negative.");
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("averageCost must not be negative.");

        this.portfolioHoldingId = portfolioHoldingId;
        this.portfolioId        = portfolioId;
        this.instrumentId       = instrumentId;
        this.quantity           = quantity.stripTrailingZeros();
        this.averageCost        = averageCost.stripTrailingZeros();
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    public boolean hasEnough(BigDecimal requestedQuantity) {
        if (requestedQuantity == null) throw new IllegalArgumentException("requestedQuantity must not be null.");
        return quantity.compareTo(requestedQuantity) >= 0;
    }

    public void buy(BigDecimal units, BigDecimal price) {
        validatePositive(units, "buy units");
        validatePositive(price, "buy price");

        BigDecimal oldValue  = quantity.multiply(averageCost, AVG_COST_MC);
        BigDecimal newValue  = units.multiply(price, AVG_COST_MC);
        BigDecimal newQty    = quantity.add(units);

        averageCost = oldValue.add(newValue).divide(newQty, AVG_COST_MC);
        quantity    = newQty;
    }

    public void sell(BigDecimal units) {
        validatePositive(units, "sell units");
        if (!hasEnough(units)) {
            throw new IllegalStateException(
                "Cannot sell " + units.toPlainString()
                + " units: holding is only " + quantity.toPlainString() + " units.");
        }
        quantity = quantity.subtract(units);
    }

    public Long getPortfolioHoldingId() { return portfolioHoldingId; }
    public Long getPortfolioId()        { return portfolioId; }
    public Long getInstrumentId()       { return instrumentId; }
    public BigDecimal getQuantity()     { return quantity; }
    public BigDecimal getAverageCost()  { return averageCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private static void validatePositive(BigDecimal value, String label) {
        if (value == null)
            throw new IllegalArgumentException(label + " must not be null.");
        if (value.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException(label + " must be positive; got " + value.toPlainString());
    }

    @Override
    public String toString() {
        return "PortfolioHolding{portfolioHoldingId=" + portfolioHoldingId
            + ", portfolioId=" + portfolioId + ", instrumentId=" + instrumentId
            + ", quantity=" + quantity + ", averageCost=" + averageCost + "}";
    }
}