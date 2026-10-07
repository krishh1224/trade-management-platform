package com.team5.advice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdviceSignalResponse(
    String symbol,
    String instrumentName,
    String origin,
    String signal,
    List<String> reasoning,
    BigDecimal lastPrice,
    BigDecimal changePct,
    BigDecimal unrealisedPnlPercent,
    String currency,
    Instant priceAsOf,
    boolean stale
) {
}