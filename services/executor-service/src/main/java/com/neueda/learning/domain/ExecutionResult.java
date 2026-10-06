package com.neueda.learning.domain;

import com.neueda.learning.domain.enums.ExecutionStatus;
import java.math.BigDecimal;

/**
 * Result of trade execution: contains decision, order info, and reason.
 * Used by the service to determine final order resolution (FILLED/REJECTED).
 *
 * A FILLED result produced by TradeExecutionService also carries the settlement figures:
 * the FX rate applied, the account's base currency and the unsigned cash amount in that
 * currency (executionPrice * quantity * fxRate). They are null on REJECTED results.
 */
public class ExecutionResult {
    private final Long orderId;
    private final ExecutionStatus status;
    private final BigDecimal executionPrice;
    private final String reason;
    private final BigDecimal fxRate;
    private final String settlementCurrency;
    private final BigDecimal settlementAmount;

    public ExecutionResult(Long orderId, ExecutionStatus status, BigDecimal executionPrice, String reason) {
        this(orderId, status, executionPrice, reason, null, null, null);
    }

    public ExecutionResult(Long orderId, ExecutionStatus status, BigDecimal executionPrice, String reason,
                           BigDecimal fxRate, String settlementCurrency, BigDecimal settlementAmount) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.orderId = orderId;
        this.status = status;
        this.executionPrice = executionPrice;
        this.reason = reason;
        this.fxRate = fxRate;
        this.settlementCurrency = settlementCurrency;
        this.settlementAmount = settlementAmount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public String getSettlementCurrency() {
        return settlementCurrency;
    }

    public BigDecimal getSettlementAmount() {
        return settlementAmount;
    }

    public boolean isFilled() {
        return status == ExecutionStatus.FILLED;
    }

    public boolean isRejected() {
        return status == ExecutionStatus.REJECTED;
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "orderId=" + orderId +
                ", status=" + status +
                ", executionPrice=" + executionPrice +
                ", reason='" + reason + '\'' +
                ", fxRate=" + fxRate +
                ", settlementCurrency='" + settlementCurrency + '\'' +
                ", settlementAmount=" + settlementAmount +
                '}';
    }
}
