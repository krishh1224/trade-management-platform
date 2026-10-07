package com.team5.portfolio.fx;

import com.team5.portfolio.pricing.PricedQuote;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up the Fauxnance pair FX:{from}{to}, which quotes the units of {@code to} per one unit of
 * {@code from} (FX:USDINR = INR per USD). An empty result means the currency cannot be converted.
 */
public final class FxConverter {

    private FxConverter() {
    }

    public static String pair(String from, String to) {
        return "FX:" + from + to;
    }

    public static Optional<BigDecimal> rate(String from, String to, Map<String, PricedQuote> quotes) {
        if (from.equals(to)) {
            return Optional.of(BigDecimal.ONE);
        }
        PricedQuote quote = quotes.get(pair(from, to));
        return quote == null ? Optional.empty() : Optional.of(quote.price());
    }
}
