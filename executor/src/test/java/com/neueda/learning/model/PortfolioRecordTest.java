package com.neueda.learning.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PortfolioRecordTest {

    @Test
    void allArgsConstructorPopulatesAllGetters() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        PortfolioRecord record = new PortfolioRecord(201L, "Aarav Equity Portfolio", 101L, "ACTIVE", now, now);

        assertEquals(201L, record.getPortfolioId());
        assertEquals("Aarav Equity Portfolio", record.getName());
        assertEquals(101L, record.getAccountId());
        assertEquals("ACTIVE", record.getStatus());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        PortfolioRecord record = new PortfolioRecord();
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        record.setPortfolioId(202L);
        record.setName("Diya Equity Portfolio");
        record.setAccountId(102L);
        record.setStatus("CLOSED");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        assertEquals(202L, record.getPortfolioId());
        assertEquals("Diya Equity Portfolio", record.getName());
        assertEquals(102L, record.getAccountId());
        assertEquals("CLOSED", record.getStatus());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void toStringContainsKeyFields() {
        PortfolioRecord record = new PortfolioRecord(201L, "Aarav Equity Portfolio", 101L, "ACTIVE", null, null);

        String text = record.toString();

        assertTrue(text.contains("portfolioId=201"));
        assertTrue(text.contains("name='Aarav Equity Portfolio'"));
        assertTrue(text.contains("status='ACTIVE'"));
    }
}
