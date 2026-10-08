package com.team5.strategies.dto;

import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.domain.StrategyType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StrategyResponse(
    long strategyId,
    long accountId,
    String name,
    String symbol,
    StrategyType type,
    Integer shortWindow,
    Integer longWindow,
    Integer bandWindow,
    BigDecimal bandWidth,
    int quantity,
    BigDecimal maxSpend,
    BigDecimal spent,
    BigDecimal remainingSpend,
    long maxPosition,
    int maxConsecutiveFailures,
    int consecutiveFailures,
    int ordersPlaced,
    StrategyStatus status,
    String statusReason,
    OffsetDateTime lastTriggeredAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static StrategyResponse from(StrategyRow row) {
        return new StrategyResponse(
            row.getStrategyId(),
            row.getAccountId(),
            row.getName(),
            row.getSymbol(),
            row.getStrategyType(),
            row.getShortWindow(),
            row.getLongWindow(),
            row.getBandWindow(),
            row.getBandWidth(),
            row.getQuantity(),
            row.getMaxSpend(),
            row.getSpent(),
            row.remainingSpend(),
            row.getMaxPosition(),
            row.getMaxConsecutiveFailures(),
            row.getConsecutiveFailures(),
            row.getOrdersPlaced(),
            row.getStatus(),
            row.getStatusReason(),
            row.getLastTriggeredAt(),
            row.getCreatedAt(),
            row.getUpdatedAt()
        );
    }
}
