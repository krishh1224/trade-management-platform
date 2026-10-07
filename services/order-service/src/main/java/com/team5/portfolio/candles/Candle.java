package com.team5.portfolio.candles;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily bar. {@code synthetic} marks a bar the provider estimated rather than observed. */
public record Candle(
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    boolean synthetic
) {
}
