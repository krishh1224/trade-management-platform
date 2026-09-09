package com.team5.dto.response;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderHistoryEntry(
    String orderId,
    Long accountId,
    String symbol,
    OrderSide side,
    Integer quantity,
    BigDecimal price,
    BigDecimal executedPrice,
    OrderStatus status,
    String idempotencyKey,
    OffsetDateTime createdOn
) {
}
