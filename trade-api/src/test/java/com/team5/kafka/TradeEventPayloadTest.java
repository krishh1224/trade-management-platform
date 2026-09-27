package com.team5.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradeEventPayloadTest {

    @Test
    void settersAndGettersRoundTripAllFields() {
        TradeEventPayload payload = new TradeEventPayload();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime executedOn = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        payload.setOrderId(orderId);
        payload.setAccountId(101L);
        payload.setSymbol("INFY.NS");
        payload.setSide("BUY");
        payload.setQuantity(10);
        payload.setPrice(new BigDecimal("1500.00"));
        payload.setExecutedPrice(new BigDecimal("1500.50"));
        payload.setStatus("FILLED");
        payload.setReason(null);
        payload.setCashDelta(new BigDecimal("-15005.00"));
        payload.setPositionQuantityAfter(10);
        payload.setAverageCostAfter(new BigDecimal("1500.50"));
        payload.setExecutedOn(executedOn);

        assertEquals(orderId, payload.getOrderId());
        assertEquals(101L, payload.getAccountId());
        assertEquals("INFY.NS", payload.getSymbol());
        assertEquals("BUY", payload.getSide());
        assertEquals(10, payload.getQuantity());
        assertEquals(new BigDecimal("1500.00"), payload.getPrice());
        assertEquals(new BigDecimal("1500.50"), payload.getExecutedPrice());
        assertEquals("FILLED", payload.getStatus());
        assertEquals(null, payload.getReason());
        assertEquals(new BigDecimal("-15005.00"), payload.getCashDelta());
        assertEquals(10, payload.getPositionQuantityAfter());
        assertEquals(new BigDecimal("1500.50"), payload.getAverageCostAfter());
        assertEquals(executedOn, payload.getExecutedOn());
    }

    @Test
    void rejectionReasonRoundTrips() {
        TradeEventPayload payload = new TradeEventPayload();

        payload.setStatus("REJECTED");
        payload.setReason("PRICE_NOT_MET");

        assertEquals("REJECTED", payload.getStatus());
        assertEquals("PRICE_NOT_MET", payload.getReason());
    }
}
