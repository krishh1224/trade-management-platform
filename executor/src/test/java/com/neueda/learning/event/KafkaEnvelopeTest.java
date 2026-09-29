package com.neueda.learning.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaEnvelopeTest {

    @Test
    void allArgsConstructorPopulatesAllGetters() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OrderPlacedPayload payload = new OrderPlacedPayload();

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            eventId, "ORDER_PLACED", eventTime, "trade-api", 1, payload
        );

        assertEquals(eventId, envelope.getEventId());
        assertEquals("ORDER_PLACED", envelope.getEventType());
        assertEquals(eventTime, envelope.getEventTime());
        assertEquals("trade-api", envelope.getSource());
        assertEquals(1, envelope.getSchemaVersion());
        assertEquals(payload, envelope.getPayload());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        KafkaEnvelope<String> envelope = new KafkaEnvelope<>();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        envelope.setEventId(eventId);
        envelope.setEventType("QUOTE");
        envelope.setEventTime(eventTime);
        envelope.setSource("market-poller");
        envelope.setSchemaVersion(2);
        envelope.setPayload("payload-body");

        assertEquals(eventId, envelope.getEventId());
        assertEquals("QUOTE", envelope.getEventType());
        assertEquals(eventTime, envelope.getEventTime());
        assertEquals("market-poller", envelope.getSource());
        assertEquals(2, envelope.getSchemaVersion());
        assertEquals("payload-body", envelope.getPayload());
    }

    @Test
    void toStringContainsKeyFields() {
        UUID eventId = UUID.randomUUID();
        KafkaEnvelope<String> envelope = new KafkaEnvelope<>(
            eventId, "QUOTE", OffsetDateTime.parse("2026-01-01T00:00:00Z"), "executor", 1, "body"
        );

        String text = envelope.toString();

        assertTrue(text.contains("eventType='QUOTE'"));
        assertTrue(text.contains("source='executor'"));
        assertTrue(text.contains("schemaVersion=1"));
    }
}
