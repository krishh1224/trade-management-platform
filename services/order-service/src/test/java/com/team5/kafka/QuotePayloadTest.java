package com.team5.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class QuotePayloadTest {

    @Test
    void settersAndGettersRoundTripAllFields() {
        QuotePayload payload = new QuotePayload();
        OffsetDateTime quoteAsOf = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        payload.setSymbol("INFY.NS");
        payload.setPrice(new BigDecimal("1500.00"));
        payload.setBid(new BigDecimal("1499.50"));
        payload.setAsk(new BigDecimal("1500.50"));
        payload.setCurrency("INR");
        payload.setChange(new BigDecimal("1.25"));
        payload.setChangePercent(new BigDecimal("0.08"));
        payload.setPreviousClose(new BigDecimal("1498.75"));
        payload.setMarketState("open");
        payload.setStale(false);
        payload.setQuoteAsOf(quoteAsOf);

        assertEquals("INFY.NS", payload.getSymbol());
        assertEquals(new BigDecimal("1500.00"), payload.getPrice());
        assertEquals(new BigDecimal("1499.50"), payload.getBid());
        assertEquals(new BigDecimal("1500.50"), payload.getAsk());
        assertEquals("INR", payload.getCurrency());
        assertEquals(new BigDecimal("1.25"), payload.getChange());
        assertEquals(new BigDecimal("0.08"), payload.getChangePercent());
        assertEquals(new BigDecimal("1498.75"), payload.getPreviousClose());
        assertEquals("open", payload.getMarketState());
        assertFalse(payload.isStale());
        assertEquals(quoteAsOf, payload.getQuoteAsOf());
    }

    @Test
    void staleFlagRoundTripsTrue() {
        QuotePayload payload = new QuotePayload();

        payload.setStale(true);

        assertTrue(payload.isStale());
    }
}
