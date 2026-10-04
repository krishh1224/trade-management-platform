package com.neueda.learning.client;

import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
                50L,    // initial backoff (ms)
                500L,   // max backoff (ms)
                "test-api-key"  // fauxnance API key
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
                        new FauxnanceQuoteClient.FauxnanceQuoteResponse.Data(
                                new BigDecimal("498.123456"),
                                new BigDecimal("499.654321")
                        )
                );

        when(restTemplate.exchange(
                contains("/quotes/" + symbol),
                eq(HttpMethod.GET),
                any(),
                eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Quote quote = quoteClient.getQuote(symbol);

        assertNotNull(quote);
        assertEquals(symbol, quote.getSymbol());
        // Should be scaled to 2 decimals
        assertEquals(new BigDecimal("498.12"), quote.getBid());
        assertEquals(new BigDecimal("499.65"), quote.getAsk());

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class));
    }

    /**
     * Test: Quote unavailable after max retries.
     */
    @Test
    void testQuoteUnavailableAfterRetries() {
        String symbol = "INFY.NS";

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenThrow(new RestClientException("Connection timeout"));

        QuoteUnavailableException exception = assertThrows(QuoteUnavailableException.class, () ->
                quoteClient.getQuote(symbol)
        );

        assertTrue(exception.getMessage().contains("Quote unavailable"));
        assertTrue(exception.getMessage().contains(symbol));

        // Should have attempted maxRetries + 1 times
        verify(restTemplate, times(4)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class));
    }

    /**
     * Test: Quote available on retry (success after failure).
     */
    @Test
    void testQuoteSuccessfulAfterRetry() throws InterruptedException {
        String symbol = "INFY.NS";
        FauxnanceQuoteClient.FauxnanceQuoteResponse response =
                new FauxnanceQuoteClient.FauxnanceQuoteResponse(
                        new FauxnanceQuoteClient.FauxnanceQuoteResponse.Data(
                                new BigDecimal("498.00"),
                                new BigDecimal("499.00")
                        )
                );

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenThrow(new RestClientException("First attempt fails"))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));  // Second attempt succeeds

        Quote quote = quoteClient.getQuote(symbol);

        assertNotNull(quote);
        assertEquals(symbol, quote.getSymbol());

        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class));
    }

    /**
     * Test: FX rates keep full precision (FX:INRUSD 0.010399 would round to 0.01 at 2dp).
     */
    @Test
    void testFxRateKeepsFullPrecision() {
        FauxnanceQuoteClient.FauxnanceQuoteResponse response =
                new FauxnanceQuoteClient.FauxnanceQuoteResponse(
                        new FauxnanceQuoteClient.FauxnanceQuoteResponse.Data(
                                new BigDecimal("0.010399"),
                                new BigDecimal("0.010401"),
                                "USD"
                        )
                );

        when(restTemplate.exchange(
                eq("http://localhost:8081/quotes/FX:INRUSD"),
                eq(HttpMethod.GET),
                any(),
                eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Quote rate = quoteClient.getFxRate("INR", "USD");

        assertEquals("FX:INRUSD", rate.getSymbol());
        assertEquals(new BigDecimal("0.010399"), rate.getBid());
        assertEquals(new BigDecimal("0.010401"), rate.getAsk());
    }

    /**
     * Test: an FX pair quoted in a currency other than the target is not used.
     */
    @Test
    void testFxRateInUnexpectedCurrencyIsUnavailable() {
        FauxnanceQuoteClient.FauxnanceQuoteResponse response =
                new FauxnanceQuoteClient.FauxnanceQuoteResponse(
                        new FauxnanceQuoteClient.FauxnanceQuoteResponse.Data(
                                new BigDecimal("96.169038"),
                                new BigDecimal("96.170962"),
                                "USD"
                        )
                );

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        QuoteUnavailableException exception = assertThrows(QuoteUnavailableException.class, () ->
                quoteClient.getFxRate("USD", "INR")
        );

        assertTrue(exception.getMessage().contains("expected INR"));
    }

    /**
     * Test: Null response handling.
     */
    @Test
    void testNullResponseHandling() {
        String symbol = "INFY.NS";

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(FauxnanceQuoteClient.FauxnanceQuoteResponse.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        QuoteUnavailableException exception = assertThrows(QuoteUnavailableException.class, () ->
                quoteClient.getQuote(symbol)
        );

        assertTrue(exception.getMessage().contains("Quote unavailable"));
    }
}

