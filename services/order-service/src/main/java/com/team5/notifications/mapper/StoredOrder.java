package com.team5.notifications.mapper;

import java.math.BigDecimal;

public record StoredOrder(
    String symbol,
    String side,
    Integer quantity,
    BigDecimal price
) {
}
