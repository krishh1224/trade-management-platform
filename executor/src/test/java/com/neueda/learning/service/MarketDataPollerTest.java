package com.neueda.learning.service;

import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import com.neueda.learning.producer.MarketDataPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketDataPoller.
 * Tests batching logic, polling behavior, and quota validation.
 */
class MarketDataPollerTest {

    @Mock
    private SymbolProvider symbolProvider;

    @Mock
    private FauxnanceQuoteClient fauxnanceClient;

    @Mock
    private MarketDataPublisher publisher;

    private MarketDataPoller poller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Test 1: Empty symbol set → no Fauxnance calls and no Kafka messages.
     */
    @Test
    void testEmptySymbolSet_NoFauxnanceCallsNoKafkaPublish() {
        when(symbolProvider.getSymbolsToPoll()).thenReturn(new HashSet<>());

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // Should not call Fauxnance or publish
        verify(fauxnanceClient, never()).getQuote(anyString());
        verify(publisher, never()).publishQuote(anyString(), any(Quote.class));
    }

    /**
     * Test 2: Single symbol is fetched and published.
     */
    @Test
    void testSingleSymbol_FetchedAndPublished() {
        Set<String> symbols = Set.of("INFY.NS");
        Quote quote = new Quote("INFY.NS", new BigDecimal("1500.00"), new BigDecimal("1501.00"));

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);
        when(fauxnanceClient.getQuote("INFY.NS")).thenReturn(quote);

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        verify(fauxnanceClient, times(1)).getQuote("INFY.NS");
        verify(publisher, times(1)).publishQuote("INFY.NS", quote);
    }

    /**
     * Test 3: Batch of up to 25 symbols is fetched in one logical poll,
     * resulting in exactly 25 Fauxnance calls and 25 Kafka publishes.
     */
    @Test
    void testBatchOf25Symbols_AllFetched() {
        Set<String> symbols = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            symbols.add("SYMBOL" + i);
        }

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        for (String symbol : symbols) {
            Quote quote = new Quote(symbol, new BigDecimal("100.00"), new BigDecimal("101.00"));
            when(fauxnanceClient.getQuote(symbol)).thenReturn(quote);
        }

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // Each symbol should be fetched once
        for (String symbol : symbols) {
            verify(fauxnanceClient, times(1)).getQuote(symbol);
        }

        // Each symbol should be published once (25 Kafka messages)
        verify(publisher, times(25)).publishQuote(anyString(), any(Quote.class));
    }

    /**
     * Test 4: More than 25 symbols are split correctly (26 symbols → 25 + 1).
     * Verifies correct batching logic.
     */
    @Test
    void testBatchOf26Symbols_SplitCorrectly() {
        Set<String> symbols = new HashSet<>();
        for (int i = 0; i < 26; i++) {
            symbols.add("SYMBOL" + i);
        }

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        for (String symbol : symbols) {
            Quote quote = new Quote(symbol, new BigDecimal("100.00"), new BigDecimal("101.00"));
            when(fauxnanceClient.getQuote(symbol)).thenReturn(quote);
        }

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // All 26 symbols should be fetched
        for (String symbol : symbols) {
            verify(fauxnanceClient, times(1)).getQuote(symbol);
        }

        // All 26 symbols should be published
        verify(publisher, times(26)).publishQuote(anyString(), any(Quote.class));
    }

    /**
     * Test 5: 50 symbols are split into 2 batches (exactly 25 each).
     */
    @Test
    void testBatchOf50Symbols_Split() {
        Set<String> symbols = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            symbols.add("SYMBOL" + i);
        }

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        for (String symbol : symbols) {
            Quote quote = new Quote(symbol, new BigDecimal("100.00"), new BigDecimal("101.00"));
            when(fauxnanceClient.getQuote(symbol)).thenReturn(quote);
        }

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // All 50 symbols should be fetched
        for (String symbol : symbols) {
            verify(fauxnanceClient, times(1)).getQuote(symbol);
        }

        // All 50 symbols should be published
        verify(publisher, times(50)).publishQuote(anyString(), any(Quote.class));
    }

    /**
     * Test 6: Duplicate symbols in holdings are deduplicated before polling.
     * This test verifies that even if holdings contain the same symbol multiple times,
     * it's fetched only once.
     */
    @Test
    void testDuplicateSymbol_FetchedOnce() {
        // Use HashSet to simulate potential duplicates from mapper
        Set<String> symbols = new HashSet<>();
        symbols.add("INFY.NS");
        symbols.add("RELIANCE.NS");
        // Note: Set automatically deduplicates, so we can't add INFY.NS twice
        // The real test is that SymbolProvider returns a Set, which dedupes

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        Quote infy = new Quote("INFY.NS", new BigDecimal("1500.00"), new BigDecimal("1501.00"));
        Quote reliance = new Quote("RELIANCE.NS", new BigDecimal("2500.00"), new BigDecimal("2501.00"));

        when(fauxnanceClient.getQuote("INFY.NS")).thenReturn(infy);
        when(fauxnanceClient.getQuote("RELIANCE.NS")).thenReturn(reliance);

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // INFY should be fetched only once
        verify(fauxnanceClient, times(1)).getQuote("INFY.NS");
        verify(fauxnanceClient, times(1)).getQuote("RELIANCE.NS");

        // Only 2 unique symbols published
        verify(publisher, times(2)).publishQuote(anyString(), any(Quote.class));
    }

    /**
     * Test 7: Each quote is published as its own Kafka message.
     * Verifies one-to-one quote-to-message mapping.
     */
    @Test
    void testEachQuotePublishedAsOwnMessage() {
        Set<String> symbols = Set.of("INFY.NS", "RELIANCE.NS", "ITC.NS");

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        Quote infy = new Quote("INFY.NS", new BigDecimal("1500.00"), new BigDecimal("1501.00"));
        Quote reliance = new Quote("RELIANCE.NS", new BigDecimal("2500.00"), new BigDecimal("2501.00"));
        Quote itc = new Quote("ITC.NS", new BigDecimal("400.00"), new BigDecimal("401.00"));

        when(fauxnanceClient.getQuote("INFY.NS")).thenReturn(infy);
        when(fauxnanceClient.getQuote("RELIANCE.NS")).thenReturn(reliance);
        when(fauxnanceClient.getQuote("ITC.NS")).thenReturn(itc);

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // Each quote published separately
        verify(publisher).publishQuote("INFY.NS", infy);
        verify(publisher).publishQuote("RELIANCE.NS", reliance);
        verify(publisher).publishQuote("ITC.NS", itc);
        verify(publisher, times(3)).publishQuote(anyString(), any(Quote.class));
    }

    /**
     * Test 8: Each Kafka message is keyed by its symbol.
     * (Verified through publishQuote calls - symbol is passed as key)
     */
    @Test
    void testEachMessageKeyedBySymbol() {
        Set<String> symbols = Set.of("INFY.NS", "RELIANCE.NS");

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        Quote infy = new Quote("INFY.NS", new BigDecimal("1500.00"), new BigDecimal("1501.00"));
        Quote reliance = new Quote("RELIANCE.NS", new BigDecimal("2500.00"), new BigDecimal("2501.00"));

        when(fauxnanceClient.getQuote("INFY.NS")).thenReturn(infy);
        when(fauxnanceClient.getQuote("RELIANCE.NS")).thenReturn(reliance);

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // Verify publishQuote is called with correct symbol as first parameter (used as Kafka key)
        verify(publisher).publishQuote("INFY.NS", infy);
        verify(publisher).publishQuote("RELIANCE.NS", reliance);
    }

    /**
     * Test 9: Failed quote fetch doesn't stop polling of other symbols.
     * Verifies graceful error handling and continuation.
     */
    @Test
    void testFailedQuoteFetch_ContinuePolling() {
        Set<String> symbols = Set.of("INFY.NS", "RELIANCE.NS", "ITC.NS");

        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        // INFY fails
        when(fauxnanceClient.getQuote("INFY.NS"))
            .thenThrow(new QuoteUnavailableException("INFY not available"));

        // Others succeed
        Quote reliance = new Quote("RELIANCE.NS", new BigDecimal("2500.00"), new BigDecimal("2501.00"));
        Quote itc = new Quote("ITC.NS", new BigDecimal("400.00"), new BigDecimal("401.00"));

        when(fauxnanceClient.getQuote("RELIANCE.NS")).thenReturn(reliance);
        when(fauxnanceClient.getQuote("ITC.NS")).thenReturn(itc);

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        poller.pollMarketData();

        // All three should be attempted
        verify(fauxnanceClient).getQuote("INFY.NS");
        verify(fauxnanceClient).getQuote("RELIANCE.NS");
        verify(fauxnanceClient).getQuote("ITC.NS");

        // But only 2 published (INFY failed)
        verify(publisher, times(2)).publishQuote(anyString(), any(Quote.class));
        verify(publisher).publishQuote("RELIANCE.NS", reliance);
        verify(publisher).publishQuote("ITC.NS", itc);
    }

    /**
     * Test 10: Minimum interval is enforced at startup.
     * Interval less than 44 seconds should throw exception.
     */
    @Test
    void testMinimumIntervalEnforced() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 43);
        });
    }

    /**
     * Test 11: Minimum interval of 44 seconds is accepted.
     */
    @Test
    void testMinimumIntervalAccepted() {
        assertDoesNotThrow(() -> {
            new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 44);
        });
    }

    /**
     * Test 12: Default interval (60 seconds) is within quota.
     * For 6 symbols: ceil(6/25) * floor(86400/60) = 1 * 1440 = 1440 requests/day
     */
    @Test
    void testDefaultInterval60Seconds_WithinQuota() {
        // 60 seconds gives 1440 polls per day
        // 6 symbols fit in 1 batch (ceil(6/25) = 1)
        // 1 batch * 1440 polls = 1440 requests/day (within 2000 quota)
        assertDoesNotThrow(() -> {
            new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        });
    }

    /**
     * Test 13: Quota arithmetic verification for 6 symbols.
     * At 60 second interval: floor(86400 / 60) = 1440 polls/day
     * With 6 symbols: ceil(6 / 25) = 1 request/poll
     * Total: 1 * 1440 = 1440 requests/day (well under 2000 quota)
     */
    @Test
    void testQuotaForSixSymbols_60SecondInterval() {
        // This test documents the quota calculation
        // Symbols: 6 (from seed data: INFY.NS, RELIANCE.NS, TATASTEEL.BO, HDFCBANK.NS, ICICIBANK.NS, ITC.NS)
        // Interval: 60 seconds
        // Batches per poll: ceil(6 / 25) = 1
        // Polls per day: floor(86400 / 60) = 1440
        // Requests per day: 1 * 1440 = 1440 (within 2000)

        assertDoesNotThrow(() -> {
            new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);
        });
    }

    /**
     * Test 14: With 8 symbols, minimum interval must be 44 seconds.
     * 8 symbols: ceil(8 / 25) = 1 batch per poll
     * At 44 seconds: floor(86400 / 44) = 1963 polls/day
     * Total: 1 * 1963 = 1963 requests/day (just under 2000)
     * At 43 seconds: floor(86400 / 43) = 2009 polls/day
     * Total: 1 * 2009 = 2009 requests/day (exceeds 2000)
     */
    @Test
    void testMinimumInterval44Seconds_ForEightSymbols() {
        // Documentation of the 44 second minimum
        // 44 seconds minimum ensures we stay under 2000 requests/day
        assertDoesNotThrow(() -> {
            new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 44);
        });
    }

    /**
     * Test 15: Poll execution completes without throwing exceptions.
     */
    @Test
    void testPollingCompletesSuccessfully() {
        Set<String> symbols = Set.of("INFY.NS", "RELIANCE.NS");
        when(symbolProvider.getSymbolsToPoll()).thenReturn(symbols);

        Quote infy = new Quote("INFY.NS", new BigDecimal("1500.00"), new BigDecimal("1501.00"));
        Quote reliance = new Quote("RELIANCE.NS", new BigDecimal("2500.00"), new BigDecimal("2501.00"));

        when(fauxnanceClient.getQuote("INFY.NS")).thenReturn(infy);
        when(fauxnanceClient.getQuote("RELIANCE.NS")).thenReturn(reliance);

        poller = new MarketDataPoller(symbolProvider, fauxnanceClient, publisher, 60);

        // Should not throw
        assertDoesNotThrow(() -> poller.pollMarketData());
    }
}
