package com.neueda.learning.service;

import com.neueda.learning.mapper.PortfolioHoldingMapper;
import com.neueda.learning.mapper.StrategySymbolMapper;
import com.neueda.learning.mapper.WatchlistSymbolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SymbolProvider.
 * Tests symbol retrieval and deduplication logic.
 */
class SymbolProviderTest {

    @Mock
    private PortfolioHoldingMapper holdingMapper;

    @Mock
    private WatchlistSymbolMapper watchlistSymbolMapper;

    @Mock
    private StrategySymbolMapper strategySymbolMapper;

    private SymbolProvider symbolProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        symbolProvider = new SymbolProvider(holdingMapper, watchlistSymbolMapper, strategySymbolMapper);
    }

    /**
     * Test: Empty holdings returns empty symbol set.
     */
    @Test
    void testEmptyHoldings_ReturnsEmptySet() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of());
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of());

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertTrue(symbols.isEmpty());
    }

    /**
     * Test: Single holding returns single symbol.
     */
    @Test
    void testSingleHolding_ReturnsSingleSymbol() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of("INFY.NS"));
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of());

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(1, symbols.size());
        assertTrue(symbols.contains("INFY.NS"));
    }

    /**
     * Test: Multiple distinct holdings return all symbols.
     */
    @Test
    void testMultipleDistinctHoldings_ReturnsAllSymbols() {
        List<String> tickers = Arrays.asList("INFY.NS", "RELIANCE.NS", "TATASTEEL.BO");
        when(holdingMapper.findAllHeldSymbols()).thenReturn(tickers);
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of());

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(3, symbols.size());
        assertTrue(symbols.contains("INFY.NS"));
        assertTrue(symbols.contains("RELIANCE.NS"));
        assertTrue(symbols.contains("TATASTEEL.BO"));
    }

    /**
     * Test: Symbols are deduplicated in the returned set.
     * Even though SQL DISTINCT handles this, the service's HashSet ensures it.
     */
    @Test
    void testSymbolsAreDeduped() {
        // Although SQL DISTINCT should prevent duplicates, test that the service
        // converts to Set for additional safety
        List<String> tickers = Arrays.asList("INFY.NS", "RELIANCE.NS", "INFY.NS");
        when(holdingMapper.findAllHeldSymbols()).thenReturn(tickers);
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of("INFY.NS", "ITC.NS"));

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        // Set should deduplicate across holdings and watchlists
        assertEquals(3, symbols.size());
        assertTrue(symbols.contains("INFY.NS"));
        assertTrue(symbols.contains("RELIANCE.NS"));
        assertTrue(symbols.contains("ITC.NS"));
    }

    /**
     * Test: Six symbols (from seed data) are all returned.
     * This verifies the test scenario from the Jira.
     */
    @Test
    void testSixSymbolsFromSeed() {
        List<String> tickers = Arrays.asList(
            "INFY.NS",
            "RELIANCE.NS",
            "TATASTEEL.BO",
            "HDFCBANK.NS",
            "ICICIBANK.NS",
            "ITC.NS"
        );
        when(holdingMapper.findAllHeldSymbols()).thenReturn(tickers);
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of());

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(6, symbols.size());
        for (String ticker : tickers) {
            assertTrue(symbols.contains(ticker));
        }
    }

    @Test
    void testWatchlistOnlySymbols_AreIncluded() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of());
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of("SBIN.NS", "INFY.NS"));

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(2, symbols.size());
        assertTrue(symbols.contains("SBIN.NS"));
        assertTrue(symbols.contains("INFY.NS"));
    }

    /**
     * Test: A symbol only an enabled strategy trades is polled too, deduplicated with the others.
     */
    @Test
    void testStrategySymbols_ArePolledAndDeduplicated() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of("INFY.NS"));
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of());
        when(strategySymbolMapper.findAllStrategySymbols()).thenReturn(List.of("INFY.NS", "TCS.NS"));

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(Set.of("INFY.NS", "TCS.NS"), symbols);
    }

    /**
     * Test: A database without the strategies table still polls holdings and watchlists.
     */
    @Test
    void testStrategySymbolsUnavailable_StillPollsTheRest() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of("INFY.NS"));
        when(watchlistSymbolMapper.findAllWatchlistSymbols()).thenReturn(List.of("SBIN.NS"));
        when(strategySymbolMapper.findAllStrategySymbols())
            .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("select", "SELECT", new java.sql.SQLException("relation \"strategies\" does not exist")));

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(Set.of("INFY.NS", "SBIN.NS"), symbols);
    }
}
