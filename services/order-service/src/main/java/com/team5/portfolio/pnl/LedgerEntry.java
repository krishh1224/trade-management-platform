package com.team5.portfolio.pnl;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One booked sale. Written once and never changed.
 *
 * @param realisedPnl     (fill price - average cost at sale) * quantity, in the instrument's currency
 * @param realisedPnlBase the same, converted at the sale's FX rate into the account's base currency
 */
public record LedgerEntry(
    long orderId,
    long accountId,
    String symbol,
    String instrumentCurrency,
    String baseCurrency,
    long quantitySold,
    BigDecimal fillPrice,
    BigDecimal averageCostAtSale,
    BigDecimal fxRate,
    BigDecimal realisedPnl,
    BigDecimal realisedPnlBase,
    LocalDateTime soldAt
) {
}
