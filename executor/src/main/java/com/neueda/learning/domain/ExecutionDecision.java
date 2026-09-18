package com.neueda.learning.domain;

import com.neueda.learning.domain.enums.ExecutionStatus;
import java.math.BigDecimal;

/**
 * Pure decision function result: no database access, no HTTP, no Spring.
 * Encapsulates the execution decision based on order limit price and market quote.
 */
public class ExecutionDecision {
    private final ExecutionStatus status;
    private final BigDecimal executionPrice;
    private final String reason;

    /**
     * Private constructor.
     */
    private ExecutionDecision(ExecutionStatus status, BigDecimal executionPrice, String reason) {
        this.status = status;
        this.executionPrice = executionPrice;
        this.reason = reason;
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

    /**
     * Pure decision logic: no Spring, no DB, no HTTP.
     * Decides whether to fill or reject based on order side and limit price vs. quote.
     *
     * @param orderSide BUY or SELL (from existing domain)
     * @param limitPrice the limit price from the order
     * @param quote the market quote
     * @return ExecutionDecision with FILLED/REJECTED status
     */
    public static ExecutionDecision decide(String orderSide, BigDecimal limitPrice, Quote quote) {
        if ("BUY".equalsIgnoreCase(orderSide)) {
            return decideBuy(limitPrice, quote);
        } else if ("SELL".equalsIgnoreCase(orderSide)) {
            return decideSell(limitPrice, quote);
        } else {
            throw new IllegalArgumentException("Unknown order side: " + orderSide);
        }
    }

    private static ExecutionDecision decideBuy(BigDecimal limitPrice, Quote quote) {
        if (limitPrice.compareTo(quote.getAsk()) >= 0) {
            // Marketable: limit price >= ask
            return new ExecutionDecision(
                    ExecutionStatus.FILLED,
                    quote.getAsk(),
                    "BUY order marketable at ask price"
            );
        } else {
            // Non-marketable: limit price < ask
            return new ExecutionDecision(
                    ExecutionStatus.REJECTED,
                    null,
                    "BUY order non-marketable: limit " + limitPrice + " < ask " + quote.getAsk()
            );
        }
    }

    private static ExecutionDecision decideSell(BigDecimal limitPrice, Quote quote) {
        if (limitPrice.compareTo(quote.getBid()) <= 0) {
            // Marketable: limit price <= bid
            return new ExecutionDecision(
                    ExecutionStatus.FILLED,
                    quote.getBid(),
                    "SELL order marketable at bid price"
            );
        } else {
            // Non-marketable: limit price > bid
            return new ExecutionDecision(
                    ExecutionStatus.REJECTED,
                    null,
                    "SELL order non-marketable: limit " + limitPrice + " > bid " + quote.getBid()
            );
        }
    }

    /**
     * Factory method for rejection due to pricing unavailability.
     */
    public static ExecutionDecision rejectedDueToQuoteUnavailable() {
        return new ExecutionDecision(
                ExecutionStatus.REJECTED,
                null,
                "Quote unavailable after retries"
        );
    }

    /**
     * Factory method for rejection due to account checks.
     */
    public static ExecutionDecision rejectedDueToAccountCheck(String reason) {
        return new ExecutionDecision(
                ExecutionStatus.REJECTED,
                null,
                reason
        );
    }

    @Override
    public String toString() {
        return "ExecutionDecision{" +
                "status=" + status +
                ", executionPrice=" + executionPrice +
                ", reason='" + reason + '\'' +
                '}';
    }
}
