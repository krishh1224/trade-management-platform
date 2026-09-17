package com.neueda.learning.domain;

import com.neueda.learning.domain.enums.ExecutionStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for pure ExecutionDecision logic.
 * Tests all 6 paths without database, HTTP, or Spring.
 */
class ExecutionDecisionTest {

    /**
     * Test 1: BUY limit at/above ask → FILLED at quoted ask.
     */
    @Test
    void testBuyLimitAboveAsk_ShouldFill() {
        BigDecimal limitPrice = new BigDecimal("500.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("498.00"), new BigDecimal("499.00"));

        ExecutionDecision decision = ExecutionDecision.decide("BUY", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        assertEquals(new BigDecimal("499.00"), decision.getExecutionPrice());
        assertTrue(decision.getReason().contains("marketable at ask"));
    }

    /**
     * Test 1b: BUY limit exactly at ask → FILLED.
     */
    @Test
    void testBuyLimitAtAsk_ShouldFill() {
        BigDecimal limitPrice = new BigDecimal("499.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("498.00"), new BigDecimal("499.00"));

        ExecutionDecision decision = ExecutionDecision.decide("BUY", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        assertEquals(new BigDecimal("499.00"), decision.getExecutionPrice());
    }

    /**
     * Test 2: SELL limit at/below bid → FILLED at quoted bid.
     */
    @Test
    void testSellLimitBelowBid_ShouldFill() {
        BigDecimal limitPrice = new BigDecimal("500.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("501.00"), new BigDecimal("502.00"));

        ExecutionDecision decision = ExecutionDecision.decide("SELL", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        assertEquals(new BigDecimal("501.00"), decision.getExecutionPrice());
        assertTrue(decision.getReason().contains("marketable at bid"));
    }

    /**
     * Test 2b: SELL limit exactly at bid → FILLED.
     */
    @Test
    void testSellLimitAtBid_ShouldFill() {
        BigDecimal limitPrice = new BigDecimal("501.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("501.00"), new BigDecimal("502.00"));

        ExecutionDecision decision = ExecutionDecision.decide("SELL", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        assertEquals(new BigDecimal("501.00"), decision.getExecutionPrice());
    }

    /**
     * Test 3: BUY outside marketable range (limit < ask) → REJECTED.
     */
    @Test
    void testBuyLimitBelowAsk_ShouldReject() {
        BigDecimal limitPrice = new BigDecimal("498.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("499.00"), new BigDecimal("500.00"));

        ExecutionDecision decision = ExecutionDecision.decide("BUY", limitPrice, quote);

        assertEquals(ExecutionStatus.REJECTED, decision.getStatus());
        assertNull(decision.getExecutionPrice());
        assertTrue(decision.getReason().contains("non-marketable"));
    }

    /**
     * Test 3b: SELL outside marketable range (limit > bid) → REJECTED.
     */
    @Test
    void testSellLimitAboveBid_ShouldReject() {
        BigDecimal limitPrice = new BigDecimal("502.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("500.00"), new BigDecimal("501.00"));

        ExecutionDecision decision = ExecutionDecision.decide("SELL", limitPrice, quote);

        assertEquals(ExecutionStatus.REJECTED, decision.getStatus());
        assertNull(decision.getExecutionPrice());
        assertTrue(decision.getReason().contains("non-marketable"));
    }

    /**
     * Test: BUY with exact execution price match.
     * Example from story: BUY limit 500, ask 499 → FILLED at 499.
     */
    @Test
    void testBuyExecutionAtBetterPrice() {
        BigDecimal limitPrice = new BigDecimal("500.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("498.00"), new BigDecimal("499.00"));

        ExecutionDecision decision = ExecutionDecision.decide("BUY", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        assertEquals(new BigDecimal("499.00"), decision.getExecutionPrice());
        // Confirm we use the quoted price, not the limit
        assertTrue(decision.getExecutionPrice().compareTo(limitPrice) < 0);
    }

    /**
     * Test: SELL with exact execution price match.
     * Example from story: SELL limit 500, bid 501 → FILLED at 501.
     */
    @Test
    void testSellExecutionAtBetterPrice() {
        BigDecimal limitPrice = new BigDecimal("500.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("501.00"), new BigDecimal("502.00"));

        ExecutionDecision decision = ExecutionDecision.decide("SELL", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        assertEquals(new BigDecimal("501.00"), decision.getExecutionPrice());
        // Confirm we use the quoted price, not the limit
        assertTrue(decision.getExecutionPrice().compareTo(limitPrice) > 0);
    }

    /**
     * Test: Quote unavailable rejection factory method.
     */
    @Test
    void testQuoteUnavailableRejection() {
        ExecutionDecision decision = ExecutionDecision.rejectedDueToQuoteUnavailable();

        assertEquals(ExecutionStatus.REJECTED, decision.getStatus());
        assertNull(decision.getExecutionPrice());
        assertTrue(decision.getReason().contains("Quote unavailable"));
    }

    /**
     * Test: Account check rejection factory method.
     */
    @Test
    void testAccountCheckRejection() {
        String reason = "Insufficient funds";
        ExecutionDecision decision = ExecutionDecision.rejectedDueToAccountCheck(reason);

        assertEquals(ExecutionStatus.REJECTED, decision.getStatus());
        assertNull(decision.getExecutionPrice());
        assertEquals(reason, decision.getReason());
    }

    /**
     * Test: Precision handling - Fauxnance with many decimal places.
     */
    @Test
    void testPrecisionHandling() {
        BigDecimal limitPrice = new BigDecimal("100.00");
        // Fauxnance returns 6 decimal places
        Quote quote = new Quote("INFY.NS", 
                new BigDecimal("99.123456"), 
                new BigDecimal("99.654321"));

        ExecutionDecision decision = ExecutionDecision.decide("BUY", limitPrice, quote);

        assertEquals(ExecutionStatus.FILLED, decision.getStatus());
        // The quote client would have already scaled to 2 decimal places
        assertTrue(decision.getExecutionPrice().toString().matches("99\\.\\d{1,2}"));
    }

    /**
     * Test: Invalid order side.
     */
    @Test
    void testInvalidOrderSide_ThrowsException() {
        BigDecimal limitPrice = new BigDecimal("100.00");
        Quote quote = new Quote("INFY.NS", new BigDecimal("99.00"), new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () ->
                ExecutionDecision.decide("INVALID", limitPrice, quote)
        );
    }
}

