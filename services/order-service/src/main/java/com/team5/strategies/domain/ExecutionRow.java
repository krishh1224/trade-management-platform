package com.team5.strategies.domain;

import com.team5.domain.enums.OrderSide;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// One row of strategy_executions: what the module did when a strategy's rule fired on a quote.
public class ExecutionRow {

    private Long executionId;
    private Long strategyId;
    private UUID quoteEventId;
    private OrderSide signal;
    private ExecutionOutcome outcome;
    private BigDecimal quotePrice;
    private BigDecimal orderPrice;
    private Integer quantity;
    private String idempotencyKey;
    private String orderId;
    private String detail;
    private OffsetDateTime createdAt;

    public ExecutionRow() {
    }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    public UUID getQuoteEventId() { return quoteEventId; }
    public void setQuoteEventId(UUID quoteEventId) { this.quoteEventId = quoteEventId; }

    public OrderSide getSignal() { return signal; }
    public void setSignal(OrderSide signal) { this.signal = signal; }

    public ExecutionOutcome getOutcome() { return outcome; }
    public void setOutcome(ExecutionOutcome outcome) { this.outcome = outcome; }

    public BigDecimal getQuotePrice() { return quotePrice; }
    public void setQuotePrice(BigDecimal quotePrice) { this.quotePrice = quotePrice; }

    public BigDecimal getOrderPrice() { return orderPrice; }
    public void setOrderPrice(BigDecimal orderPrice) { this.orderPrice = orderPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
