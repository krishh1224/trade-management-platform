package com.team5.portfolio.read;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A filled order, in the instrument's currency. {@code fillPrice} is the executed price, or the limit
 * price for orders that predate the executor. {@code fxRate} is null when the order recorded none.
 */
// field order must match the findFilledOrders query in PortfolioReadMapper.xml
public record FilledOrder(
    long orderId,
    String symbol,
    String instrumentCurrency,
    String side,
    long quantity,
    BigDecimal fillPrice,
    BigDecimal fxRate,
    LocalDateTime executedAt
) {

    public boolean isSell() {
        return "SELL".equalsIgnoreCase(side);
    }
}
