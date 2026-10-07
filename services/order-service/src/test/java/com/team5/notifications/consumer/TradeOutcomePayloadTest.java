package com.team5.notifications.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.team5.domain.OrderIdentifiers;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradeOutcomePayloadTest {

    private static final UUID ORDER_UUID = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    @Test
    void numericOrderId_isUsedAsIs() {
        assertEquals(9001L, payload("9001").persistentOrderId());
    }

    @Test
    void uuidAndExternalOrderId_mapToTheSameOrdersKey() {
        long expected = OrderIdentifiers.persistentId(ORDER_UUID);

        assertEquals(expected, payload(ORDER_UUID.toString()).persistentOrderId());
        assertEquals(expected, payload("ORD-" + ORDER_UUID).persistentOrderId());
    }

    @Test
    void missingOrUnreadableOrderId_isNull() {
        assertNull(payload(null).persistentOrderId());
        assertNull(payload("not-an-id").persistentOrderId());
    }

    @Test
    void rejectionReason_winsOverReason() {
        TradeOutcomePayload payload = payload("1");
        payload.setReason("PRICE_NOT_MET");
        payload.setRejectionReason("INSUFFICIENT_FUNDS");

        assertEquals("INSUFFICIENT_FUNDS", payload.reasonCode());
    }

    private static TradeOutcomePayload payload(String orderId) {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId(orderId);
        return payload;
    }
}
