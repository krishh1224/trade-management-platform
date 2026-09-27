package com.team5.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPlacedPayloadTest {

    @Test
    void allArgsConstructorPopulatesAllGetters() {
        UUID orderId = UUID.randomUUID();
        OffsetDateTime createdOn = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId, 101L, "INFY.NS", "BUY", 10,
            new BigDecimal("1500.00"), "idem-key-1", createdOn
        );

        assertEquals(orderId, payload.getOrderId());
        assertEquals(101L, payload.getAccountId());
        assertEquals("INFY.NS", payload.getSymbol());
        assertEquals("BUY", payload.getSide());
        assertEquals(10, payload.getQuantity());
        assertEquals(new BigDecimal("1500.00"), payload.getPrice());
        assertEquals("idem-key-1", payload.getIdempotencyKey());
        assertEquals(createdOn, payload.getCreatedOn());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        OrderPlacedPayload payload = new OrderPlacedPayload();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime createdOn = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        payload.setOrderId(orderId);
        payload.setAccountId(102L);
        payload.setSymbol("RELIANCE.NS");
        payload.setSide("SELL");
        payload.setQuantity(5);
        payload.setPrice(new BigDecimal("2800.00"));
        payload.setIdempotencyKey("idem-key-2");
        payload.setCreatedOn(createdOn);

        assertEquals(orderId, payload.getOrderId());
        assertEquals(102L, payload.getAccountId());
        assertEquals("RELIANCE.NS", payload.getSymbol());
        assertEquals("SELL", payload.getSide());
        assertEquals(5, payload.getQuantity());
        assertEquals(new BigDecimal("2800.00"), payload.getPrice());
        assertEquals("idem-key-2", payload.getIdempotencyKey());
        assertEquals(createdOn, payload.getCreatedOn());
    }
}
