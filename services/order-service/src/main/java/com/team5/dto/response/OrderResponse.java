package com.team5.dto.response;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import java.math.BigDecimal;

public record OrderResponse(
    String orderId,
    OrderStatus status,
    String message,
    String symbol,
    OrderSide side,
    Integer quantity,
    BigDecimal price
) {
}
