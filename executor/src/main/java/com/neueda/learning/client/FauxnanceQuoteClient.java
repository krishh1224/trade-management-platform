package com.neueda.learning.client;

import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import java.math.BigDecimal;
import java.math.RoundingMode;

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

    public FauxnanceQuoteClient(RestTemplate restTemplate,
                                @Value("${fauxnance.base-url:http://localhost:8081}") String fauxnanceBaseUrl,
                                @Value("${fauxnance.quote.max-retries:3}") int maxRetries,
                                @Value("${fauxnance.quote.initial-backoff-ms:100}") long initialBackoffMs,
                                @Value("${fauxnance.quote.max-backoff-ms:2000}") long maxBackoffMs) {
        this.restTemplate = restTemplate;
        this.fauxnanceBaseUrl = fauxnanceBaseUrl;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    /**
     * Fetch a quote for the given symbol with retries.
     * Throws QuoteUnavailableException if all retries are exhausted.
     */
    public Quote getQuote(String symbol) {
        long backoffMs = initialBackoffMs;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("Fetching quote for {} (attempt {}/{})", symbol, attempt + 1, maxRetries + 1);
                
                String url = fauxnanceBaseUrl + "/quotes/" + symbol;
                FauxnanceQuoteResponse response = restTemplate.getForObject(url, FauxnanceQuoteResponse.class);
                
                if (response == null) {
                    throw new RestClientException("Null response from Fauxnance");
                }

                logger.debug("Quote retrieved for {}: {}", symbol, response);
                
                // Scale prices to 2 decimal places (matching Account.balance precision)
                BigDecimal bid = response.bid.setScale(2, RoundingMode.HALF_UP);
                BigDecimal ask = response.ask.setScale(2, RoundingMode.HALF_UP);
                
                return new Quote(symbol, bid, ask);
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
     */
    public static class FauxnanceQuoteResponse {
        public String symbol;
        public BigDecimal bid;
        public BigDecimal ask;

        // Default constructor for Jackson
        public FauxnanceQuoteResponse() {
        }

        public FauxnanceQuoteResponse(String symbol, BigDecimal bid, BigDecimal ask) {
            this.symbol = symbol;
            this.bid = bid;
            this.ask = ask;
        }

        @Override
        public String toString() {
            return "FauxnanceQuoteResponse{" +
                    "symbol='" + symbol + '\'' +
                    ", bid=" + bid +
                    ", ask=" + ask +
                    '}';
        }
    }
}
