package com.neueda.learning.client;

import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * HTTP client for Fauxnance quote service.
 * Implements retry logic with increasing backoff and bounded retry budget.
 */
@Component
public class FauxnanceQuoteClient {
    private static final Logger logger = LoggerFactory.getLogger(FauxnanceQuoteClient.class);

    private final RestTemplate restTemplate;
    private final String fauxnanceBaseUrl;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final String fauxnanceApiKey;

    public FauxnanceQuoteClient(RestTemplate restTemplate,
                                @Value("https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1") String fauxnanceBaseUrl,
                                @Value("${fauxnance.quote.max-retries:3}") int maxRetries,
                                @Value("${fauxnance.quote.initial-backoff-ms:100}") long initialBackoffMs,
                                @Value("${fauxnance.quote.max-backoff-ms:2000}") long maxBackoffMs,
                                @Value("${fauxnance.api-key}") String fauxnanceApiKey) {
        this.restTemplate = restTemplate;
        this.fauxnanceBaseUrl = fauxnanceBaseUrl;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.fauxnanceApiKey = fauxnanceApiKey;
    }

    /**
     * Fetch a quote for the given symbol with retries.
     * Throws QuoteUnavailableException if all retries are exhausted.
     */
    public Quote getQuote(String symbol) {
        FauxnanceQuoteResponse.Data data = fetchWithRetry(symbol);

        // Scale prices to 2 decimal places (matching Account.balance precision)
        BigDecimal bid = data.bid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ask = data.ask.setScale(2, RoundingMode.HALF_UP);

        return new Quote(symbol, bid, ask, data.changePercent);
    }

    /**
     * Fetch the FX rate that converts an amount in {@code fromCurrency} into {@code toCurrency},
     * using the Fauxnance pair {@code FX:{from}{to}} (e.g. FX:USDINR = INR per 1 USD).
     *
     * Unlike {@link #getQuote(String)} the bid/ask are NOT rounded to 2dp: a rate such as
     * FX:INRUSD (0.010399) would collapse to 0.01. Callers decide how to round the converted amount.
     * Throws QuoteUnavailableException if all retries are exhausted or the pair is quoted in an
     * unexpected currency.
     */
    public Quote getFxRate(String fromCurrency, String toCurrency) {
        String symbol = "FX:" + fromCurrency + toCurrency;
        FauxnanceQuoteResponse.Data data = fetchWithRetry(symbol);

        if (data.currency != null && !toCurrency.equalsIgnoreCase(data.currency)) {
            throw new QuoteUnavailableException(
                "FX rate " + symbol + " is quoted in " + data.currency + ", expected " + toCurrency);
        }

        return new Quote(symbol, data.bid, data.ask);
    }

    private FauxnanceQuoteResponse.Data fetchWithRetry(String symbol) {
        long backoffMs = initialBackoffMs;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("Fetching quote for {} (attempt {}/{})", symbol, attempt + 1, maxRetries + 1);

                String url = fauxnanceBaseUrl + "/quotes/" + symbol;

                HttpHeaders headers = new HttpHeaders();
                headers.set("x-api-key", fauxnanceApiKey);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));

                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<FauxnanceQuoteResponse> response =
                        restTemplate.exchange(
                                url,
                                HttpMethod.GET,
                                entity,
                                FauxnanceQuoteResponse.class
                        );

                FauxnanceQuoteResponse quoteResponse = response.getBody();

                if (quoteResponse == null) {
                    throw new RestClientException("Null response from Fauxnance");
                }
                
                if (quoteResponse.data == null || quoteResponse.data.bid == null || quoteResponse.data.ask == null) {
                    throw new RestClientException("Missing bid/ask prices in response: " + quoteResponse);
                }

                logger.debug("Quote retrieved for {}: {}", symbol, response);

                return quoteResponse.data;
            } catch (RestClientException e) {
                logger.warn("Failed to fetch quote for {} (attempt {}/{}): {}", 
                    symbol, attempt + 1, maxRetries + 1, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoffMs);
                        // Exponential backoff with cap
                        backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new QuoteUnavailableException("Quote fetch interrupted for " + symbol, ie);
                    }
                }
            }
        }

        logger.error("Quote unavailable for {} after {} retries", symbol, maxRetries + 1);
        throw new QuoteUnavailableException("Quote unavailable for " + symbol + " after " + (maxRetries + 1) + " retries");
    }

    /**
     * DTO for Fauxnance API response.
     * Response structure:
     * {
     *   "data": {
     *     "bid": 123.45,
     *     "ask": 123.50,
     *     "currency": "USD"
     *   }
     * }
     */
    public static class FauxnanceQuoteResponse {
        public Data data;

        // Default constructor for Jackson
        public FauxnanceQuoteResponse() {
        }

        public FauxnanceQuoteResponse(Data data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "FauxnanceQuoteResponse{" +
                    "data=" + data +
                    '}';
        }

        /**
         * Nested data object containing bid/ask prices.
         */
        public static class Data {
            public BigDecimal bid;
            public BigDecimal ask;
            public BigDecimal changePercent;
            public String currency; // ISO 4217 code the prices are quoted in

            // Default constructor for Jackson
            public Data() {
            }

            public Data(BigDecimal bid, BigDecimal ask) {
                this.bid = bid;
                this.ask = ask;
            }

            public Data(BigDecimal bid, BigDecimal ask, String currency) {
                this(bid, ask);
                this.currency = currency;
            }

            @Override
            public String toString() {
                return "Data{" +
                        "bid=" + bid +
                        ", ask=" + ask +
                        ", changePercent=" + changePercent +
                        ", currency=" + currency +
                        '}';
            }
        }
    }
}
