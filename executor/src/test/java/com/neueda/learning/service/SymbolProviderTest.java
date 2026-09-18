package com.neueda.learning.service;

import com.neueda.learning.mapper.PortfolioHoldingMapper;
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

    private SymbolProvider symbolProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        symbolProvider = new SymbolProvider(holdingMapper);
    }

    /**
     * Test: Empty holdings returns empty symbol set.
     */
    @Test
    void testEmptyHoldings_ReturnsEmptySet() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of());

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertTrue(symbols.isEmpty());
    }

    /**
     * Test: Single holding returns single symbol.
     */
    @Test
    void testSingleHolding_ReturnsSingleSymbol() {
        when(holdingMapper.findAllHeldSymbols()).thenReturn(List.of("INFY.NS"));

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

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        // Set should deduplicate
        assertEquals(2, symbols.size());
        assertTrue(symbols.contains("INFY.NS"));
        assertTrue(symbols.contains("RELIANCE.NS"));
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

        Set<String> symbols = symbolProvider.getSymbolsToPoll();

        assertEquals(6, symbols.size());
        for (String ticker : tickers) {
            assertTrue(symbols.contains(ticker));
        }
    }
}
