package com.neueda.learning.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderRecordTest {

    @Test
    void mainConstructorPopulatesCoreGetters() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        OrderRecord record = new OrderRecord(
            501L, 201L, 301L, 20, new BigDecimal("1500.00"), "NEW", "BUY",
            "order-501", now, now, now);

        assertEquals(501L, record.getOrderId());
        assertEquals(201L, record.getPortfolioId());
        assertEquals(301L, record.getInstrumentId());
        assertEquals(20, record.getQuantity());
        assertEquals(new BigDecimal("1500.00"), record.getPrice());
        assertEquals("NEW", record.getStatus());
        assertEquals("BUY", record.getSide());
        assertEquals("order-501", record.getIdempotencyKey());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
        assertEquals(now, record.getExpiresAt());
        assertNull(record.getExecutedPrice());
        assertNull(record.getExecutedOn());
        assertNull(record.getRejectionReason());
    }

    @Test
    void settlementFieldsRoundTripViaSetters() {
        OrderRecord record = new OrderRecord();
        LocalDateTime executedOn = LocalDateTime.of(2026, 1, 2, 0, 0);

        record.setOrderId(502L);
        record.setStatus("FILLED");
        record.setExecutedPrice(new BigDecimal("1505.00"));
        record.setExecutedOn(executedOn);
        record.setRejectionReason(null);

        assertEquals(502L, record.getOrderId());
        assertEquals("FILLED", record.getStatus());
        assertEquals(new BigDecimal("1505.00"), record.getExecutedPrice());
        assertEquals(executedOn, record.getExecutedOn());
        assertNull(record.getRejectionReason());
    }

    @Test
    void rejectionReasonRoundTrips() {
        OrderRecord record = new OrderRecord();

        record.setStatus("REJECTED");
        record.setRejectionReason("Quote unavailable after retries");

        assertEquals("REJECTED", record.getStatus());
        assertEquals("Quote unavailable after retries", record.getRejectionReason());
    }

    @Test
    void toStringContainsKeyFields() {
        OrderRecord record = new OrderRecord(
            501L, 201L, 301L, 20, new BigDecimal("1500.00"), "NEW", "BUY",
            "order-501", null, null, null);

        String text = record.toString();

        assertTrue(text.contains("orderId=501"));
        assertTrue(text.contains("status='NEW'"));
        assertTrue(text.contains("side='BUY'"));
        assertTrue(text.contains("idempotencyKey='order-501'"));
    }
}
