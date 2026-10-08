package com.neueda.learning.service;

import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Converts trade values from the instrument's currency into the account's base currency.
 *
 * Every account holds a single cash balance in its base currency (INR for all seeded accounts).
 * An order for an instrument quoted in another currency (AAPL in USD, FX:USDJPY in JPY) is
 * settled by converting its value at the live Fauxnance FX rate FX:{instrument}{base}:
 *
 *   BUY  - the client buys the foreign currency, so the rate's ASK is used.
 *   SELL - the client sells the foreign currency, so the rate's BID is used.
 *
 * The rate is held to 8dp (orders.fx_rate is DECIMAL(19,8)) and the converted amount is rounded
 * half-up to 2dp (accounts.balance is DECIMAL(19,2)), so the stored rate always reproduces the
 * stored settlement amount.
 */
@Service
public class CurrencyConversionService {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionService.class);

    public static final int RATE_SCALE = 8;
    public static final int AMOUNT_SCALE = 2;

    private final FauxnanceQuoteClient quoteClient;

    public CurrencyConversionService(FauxnanceQuoteClient quoteClient) {
        this.quoteClient = quoteClient;
    }

    public boolean requiresConversion(String fromCurrency, String toCurrency) {
        return !normalise(fromCurrency).equals(normalise(toCurrency));
    }

    /**
     * Rate that converts one unit of {@code fromCurrency} into {@code toCurrency} for an order
     * on the given side. Returns exactly 1 when the currencies match, without calling Fauxnance.
     *
     * @throws QuoteUnavailableException if the FX rate cannot be obtained
     */
    public BigDecimal rateFor(String fromCurrency, String toCurrency, String side) {
        String from = normalise(fromCurrency);
        String to = normalise(toCurrency);
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        Quote fxQuote = quoteClient.getFxRate(from, to);
        BigDecimal rate;
        if ("BUY".equalsIgnoreCase(side)) {
            rate = fxQuote.getAsk();
        } else if ("SELL".equalsIgnoreCase(side)) {
            rate = fxQuote.getBid();
        } else {
            throw new IllegalArgumentException("Unknown order side: " + side);
        }

        if (rate.signum() <= 0) {
            throw new QuoteUnavailableException("FX rate " + fxQuote.getSymbol() + " is not positive: " + rate);
        }

        BigDecimal scaled = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        logger.debug("FX rate {} -> {} for {}: {}", from, to, side, scaled);
        return scaled;
    }

    /** Converts an amount at the given rate, rounded half-up to 2dp. */
    public static BigDecimal convert(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static String normalise(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
