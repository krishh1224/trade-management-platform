package com.team5.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PricedPositionResponse(
    Long accountId,
    String symbol,
    Integer quantity,
    BigDecimal averageCost,
    BigDecimal costBasis,
    BigDecimal lastPrice,
    BigDecimal marketValue,
    BigDecimal unrealisedPnl,
    BigDecimal unrealisedPnlPercent,
    String currency,
    Instant priceAsOf,
    boolean stale
) {
}
