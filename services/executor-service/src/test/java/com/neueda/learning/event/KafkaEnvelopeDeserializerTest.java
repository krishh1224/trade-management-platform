package com.neueda.learning.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaEnvelopeDeserializerTest {

    private final KafkaEnvelopeDeserializer deserializer = new KafkaEnvelopeDeserializer();

    @Test
    void deserializesValidEnvelopeWithPayload() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
            {
              "eventId": "%s",
              "eventType": "ORDER_PLACED",
              "eventTime": "2026-01-01T00:00:00Z",
              "source": "trade-api",
              "schemaVersion": 1,
              "payload": {
                "orderId": "%s",
                "accountId": 101,
                "symbol": "INFY.NS",
                "side": "BUY",
                "quantity": 10,
                "price": 1500.00,
                "idempotencyKey": "idem-1",
                "createdOn": "2026-01-01T00:00:00Z"
              }
            }
            """.formatted(eventId, orderId);

        KafkaEnvelope<OrderPlacedPayload> envelope =
            deserializer.deserialize("orders", json.getBytes(StandardCharsets.UTF_8));

        assertEquals(eventId, envelope.getEventId());
        assertEquals("ORDER_PLACED", envelope.getEventType());
        assertEquals("trade-api", envelope.getSource());
        assertEquals(1, envelope.getSchemaVersion());
        assertEquals(orderId, envelope.getPayload().getOrderId());
        assertEquals(101L, envelope.getPayload().getAccountId());
        assertEquals("INFY.NS", envelope.getPayload().getSymbol());
        assertEquals(new BigDecimal("1500.00"), envelope.getPayload().getPrice());
    }

    @Test
    void deserializesEnvelopeWithNullPayloadWithoutThrowing() {
        String json = """
            {
              "eventId": "%s",
              "eventType": "ORDER_PLACED",
              "eventTime": "2026-01-01T00:00:00Z",
              "source": "trade-api",
              "schemaVersion": 1,
              "payload": null
            }
            """.formatted(UUID.randomUUID());

        KafkaEnvelope<OrderPlacedPayload> envelope =
            deserializer.deserialize("orders", json.getBytes(StandardCharsets.UTF_8));

        assertNull(envelope.getPayload());
    }

    @Test
    void nullDataReturnsNull() {
        assertNull(deserializer.deserialize("orders", null));
    }

    @Test
    void malformedJsonThrowsRuntimeException() {
        byte[] malformed = "{not valid json".getBytes(StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> deserializer.deserialize("orders", malformed));
    }

    @Test
    void deserializesWrappedJsonStringEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String innerJson = """
            {
              "eventId": "%s",
              "eventType": "ORDER_CANCELLED",
              "eventTime": "2026-01-01T00:00:00Z",
              "source": "executor",
              "schemaVersion": 1,
              "payload": {
                "orderId": "%s",
                "accountId": 101,
                "symbol": "INFY.NS",
                "side": "BUY",
                "quantity": 4,
                "price": 1500,
                "idempotencyKey": "idem-cancel-1",
                "createdOn": "2026-01-01T00:00:00Z"
              }
            }
            """.formatted(eventId, orderId);

        String wrappedJson = "\"" + innerJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")
            + "\"";

        KafkaEnvelope<OrderPlacedPayload> envelope =
            deserializer.deserialize("orders", wrappedJson.getBytes(StandardCharsets.UTF_8));

        assertEquals(eventId, envelope.getEventId());
        assertEquals("ORDER_CANCELLED", envelope.getEventType());
        assertEquals(orderId, envelope.getPayload().getOrderId());
        assertEquals(101L, envelope.getPayload().getAccountId());
    }

    @Test
    void configureAndCloseDoNotThrow() {
        deserializer.configure(java.util.Map.of(), false);
        deserializer.close();
    }
}
