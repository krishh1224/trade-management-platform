package com.team5.notifications.service;

import java.math.BigDecimal;

// The order details a message is written from. Any field may be null: an executor rejection
// carries no symbol, side or quantity, and they are filled from the orders table when possible.
public record OrderFacts(
    String symbol,
    String side,
    Integer quantity,
    BigDecimal price,
    BigDecimal executedPrice,
    String reasonCode
) {

    public boolean describesOrder() {
        return symbol != null && side != null && quantity != null;
    }

    public OrderFacts withOrderDetails(OrderFacts stored) {
        return new OrderFacts(
            symbol != null ? symbol : stored.symbol(),
            side != null ? side : stored.side(),
            quantity != null ? quantity : stored.quantity(),
            price != null ? price : stored.price(),
            executedPrice,
            reasonCode
        );
    }
}
