package com.neueda.learning.client;

import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FauxnanceQuoteClient.
 * Tests retry logic with bounded retry budget.
 */
@ExtendWith(MockitoExtension.class)
class FauxnanceQuoteClientTest {

    @Mock
    private RestTemplate restTemplate;

    private FauxnanceQuoteClient quoteClient;

    @BeforeEach
    void setUp() {
        quoteClient = new FauxnanceQuoteClient(
                restTemplate,
                "http://localhost:8081",
                3,      // max retries
                50,     // initial backoff
                500     // max backoff
        );
    }

    /**
     * Test: Quote available on first attempt.
     */
    @Test
    void testQuoteSuccessfulOnFirstAttempt() {
        String symbol = "INFY.NS";
        FauxnanceQuoteClient.FauxnanceQuoteResponse response =
                new FauxnanceQuoteClient.FauxnanceQuoteResponse(
                        symbol,
                        new BigDecimal("498.123456"),
                        new BigDecimal("499.654321")
                );

        when(restTemplate.getForObject("http://localhost:8081/quotes/" + symbol,
                FauxnanceQuoteClient.FauxnanceQuoteResponse.class))
                .thenReturn(response);

        Quote quote = quoteClient.getQuote(symbol);

        assertNotNull(quote);
        assertEquals(symbol, quote.getSymbol());
        // Should be scaled to 2 decimals
        assertEquals(new BigDecimal("498.12"), quote.getBid());
        assertEquals(new BigDecimal("499.65"), quote.getAsk());

        verify(restTemplate, times(1)).getForObject(anyString(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class));
    }

    /**
     * Test: Quote unavailable after max retries.
     */
    @Test
    void testQuoteUnavailableAfterRetries() {
        String symbol = "INFY.NS";

        when(restTemplate.getForObject(anyString(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenThrow(new RestClientException("Connection timeout"));

        QuoteUnavailableException exception = assertThrows(QuoteUnavailableException.class, () ->
                quoteClient.getQuote(symbol)
        );

        assertTrue(exception.getMessage().contains("Quote unavailable"));
        assertTrue(exception.getMessage().contains(symbol));

        // Should have attempted maxRetries + 1 times
        verify(restTemplate, times(4)).getForObject(anyString(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class));
    }

    /**
     * Test: Quote available on retry (success after failure).
     */
    @Test
    void testQuoteSuccessfulAfterRetry() throws InterruptedException {
        String symbol = "INFY.NS";
        FauxnanceQuoteClient.FauxnanceQuoteResponse response =
                new FauxnanceQuoteClient.FauxnanceQuoteResponse(
                        symbol,
                        new BigDecimal("498.00"),
                        new BigDecimal("499.00")
                );

        when(restTemplate.getForObject(anyString(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenThrow(new RestClientException("First attempt fails"))
                .thenReturn(response);  // Second attempt succeeds

        Quote quote = quoteClient.getQuote(symbol);

        assertNotNull(quote);
        assertEquals(symbol, quote.getSymbol());

        verify(restTemplate, times(2)).getForObject(anyString(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class));
    }

    /**
     * Test: Null response handling.
     */
    @Test
    void testNullResponseHandling() {
        String symbol = "INFY.NS";

        when(restTemplate.getForObject(anyString(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenReturn(null);

        QuoteUnavailableException exception = assertThrows(QuoteUnavailableException.class, () ->
                quoteClient.getQuote(symbol)
        );

        assertTrue(exception.getMessage().contains("Quote unavailable"));
    }
}

