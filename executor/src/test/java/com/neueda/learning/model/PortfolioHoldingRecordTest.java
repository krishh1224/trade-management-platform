package com.neueda.learning.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PortfolioHoldingRecordTest {

    @Test
    void allArgsConstructorPopulatesAllGetters() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        PortfolioHoldingRecord record = new PortfolioHoldingRecord(
            401L, 201L, 301L, 100L, new BigDecimal("1450.00"), now, now);

        assertEquals(401L, record.getPortfolioHoldingId());
        assertEquals(201L, record.getPortfolioId());
        assertEquals(301L, record.getInstrumentId());
        assertEquals(100L, record.getQuantity());
        assertEquals(new BigDecimal("1450.00"), record.getAvgCost());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        PortfolioHoldingRecord record = new PortfolioHoldingRecord();
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        record.setPortfolioHoldingId(402L);
        record.setPortfolioId(201L);
        record.setInstrumentId(302L);
        record.setQuantity(50L);
        record.setAvgCost(new BigDecimal("2800.00"));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        assertEquals(402L, record.getPortfolioHoldingId());
        assertEquals(201L, record.getPortfolioId());
        assertEquals(302L, record.getInstrumentId());
        assertEquals(50L, record.getQuantity());
        assertEquals(new BigDecimal("2800.00"), record.getAvgCost());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void toStringContainsKeyFields() {
        PortfolioHoldingRecord record = new PortfolioHoldingRecord(
            401L, 201L, 301L, 100L, new BigDecimal("1450.00"), null, null);

        String text = record.toString();

        assertTrue(text.contains("portfolioHoldingId=401"));
        assertTrue(text.contains("quantity=100"));
        assertTrue(text.contains("avgCost=1450.00"));
    }
}
