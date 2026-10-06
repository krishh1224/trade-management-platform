package com.neueda.learning.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Quote domain class.
 */
class QuoteTest {

    @Test
    void testQuoteCreation_Success() {
        Quote quote = new Quote("INFY.NS", new BigDecimal("498.50"), new BigDecimal("499.50"));

        assertEquals("INFY.NS", quote.getSymbol());
        assertEquals(new BigDecimal("498.50"), quote.getBid());
        assertEquals(new BigDecimal("499.50"), quote.getAsk());
    }

    @Test
    void testQuoteCreation_NullSymbol_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Quote(null, new BigDecimal("498.50"), new BigDecimal("499.50"))
        );
    }

    @Test
    void testQuoteCreation_BlankSymbol_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Quote("", new BigDecimal("498.50"), new BigDecimal("499.50"))
        );
    }

    @Test
    void testQuoteCreation_NullBid_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Quote("INFY.NS", null, new BigDecimal("499.50"))
        );
    }

    @Test
    void testQuoteCreation_NullAsk_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Quote("INFY.NS", new BigDecimal("498.50"), null)
        );
    }

    @Test
    void testQuoteWithManyDecimals() {
        Quote quote = new Quote(
                "INFY.NS",
                new BigDecimal("498.123456"),
                new BigDecimal("499.654321")
        );

        assertEquals("INFY.NS", quote.getSymbol());
        assertEquals(new BigDecimal("498.123456"), quote.getBid());
        assertEquals(new BigDecimal("499.654321"), quote.getAsk());
    }

    @Test
    void testQuoteToString() {
        Quote quote = new Quote("INFY.NS", new BigDecimal("498.50"), new BigDecimal("499.50"));

        String str = quote.toString();
        assertTrue(str.contains("INFY.NS"));
        assertTrue(str.contains("498.50"));
        assertTrue(str.contains("499.50"));
    }
}

