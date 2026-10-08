package com.team5.strategies.dto;

import com.team5.domain.enums.OrderSide;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StrategyExecutionResponse(
    long executionId,
    UUID quoteEventId,
    OrderSide signal,
    ExecutionOutcome outcome,
    BigDecimal quotePrice,
    BigDecimal orderPrice,
    Integer quantity,
    String orderId,
    String detail,
    OffsetDateTime createdAt
) {

    public static StrategyExecutionResponse from(ExecutionRow row) {
        return new StrategyExecutionResponse(
            row.getExecutionId(),
            row.getQuoteEventId(),
            row.getSignal(),
            row.getOutcome(),
            row.getQuotePrice(),
            row.getOrderPrice(),
            row.getQuantity(),
            row.getOrderId(),
            row.getDetail(),
            row.getCreatedAt()
        );
    }
}
