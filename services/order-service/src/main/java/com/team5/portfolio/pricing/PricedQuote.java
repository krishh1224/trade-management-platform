package com.team5.portfolio.pricing;

import java.math.BigDecimal;
import java.time.Instant;

public record PricedQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale) {

    public PricedQuote asStale() {
        return stale ? this : new PricedQuote(symbol, price, currency, asOf, true);
    }
}
