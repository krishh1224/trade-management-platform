package com.team5.dto.response;

import java.math.BigDecimal;

public record PositionResponse(
    Long accountId,
    String symbol,
    Integer quantity,
    BigDecimal averageCost
) {
}
