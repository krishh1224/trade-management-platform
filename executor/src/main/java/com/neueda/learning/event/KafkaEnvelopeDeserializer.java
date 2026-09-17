package com.neueda.learning.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom deserializer for KafkaEnvelope<OrderPlacedPayload> messages.
 * Handles the generic type properly by using Jackson's TypeFactory.
 * The producer sends OffsetDateTime as ISO-8601 strings (e.g., "2026-09-17T08:30:45Z").
 */
public class KafkaEnvelopeDeserializer implements Deserializer<KafkaEnvelope<OrderPlacedPayload>> {
    private static final Logger logger = LoggerFactory.getLogger(KafkaEnvelopeDeserializer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaEnvelopeDeserializer() {
        // Register JavaTime module to handle OffsetDateTime deserialization from ISO-8601 strings
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void configure(java.util.Map<String, ?> configs, boolean isKey) {
        // No additional configuration needed
    }

    @Override
    public KafkaEnvelope<OrderPlacedPayload> deserialize(String topic, byte[] data) {
        if (data == null) {
            logger.warn("Null data received for topic {}", topic);
            return null;
        }

        try {
            String json = new String(data, "UTF-8");
            logger.debug("Deserializing message from topic {}: {}", topic, json);
            
            // Use Jackson's TypeFactory to handle the generic type properly
            var envelopeType = TypeFactory.defaultInstance()
                .constructParametricType(KafkaEnvelope.class, OrderPlacedPayload.class);
            
            KafkaEnvelope<OrderPlacedPayload> envelope = objectMapper.readValue(json, envelopeType);
            logger.info("Successfully deserialized: eventType={}, orderId={}, source={}", 
                envelope.getEventType(), 
                envelope.getPayload().getOrderId(),
                envelope.getSource());
            return envelope;
        } catch (Exception e) {
            logger.error("Failed to deserialize message from topic {}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Deserialization failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // No resources to close
    }
}
