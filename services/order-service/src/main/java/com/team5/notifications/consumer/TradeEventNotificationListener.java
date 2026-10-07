package com.team5.notifications.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team5.kafka.KafkaEnvelope;
import com.team5.notifications.exception.InvalidNotificationEventException;
import com.team5.notifications.service.NotificationService;
import java.util.Locale;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// First half of the pipeline: read an outcome from trade-events, record it, commit the offset.
// It never calls a provider, so a provider outage cannot stall the partition.
@Component
public class TradeEventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TradeEventNotificationListener.class);

    static final Set<String> NOTIFIABLE_EVENT_TYPES = Set.of("ORDER_FILLED", "ORDER_REJECTED", "ORDER_CANCELLED");

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final JavaType envelopeType;

    public TradeEventNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.envelopeType = objectMapper.getTypeFactory()
            .constructParametricType(KafkaEnvelope.class, TradeOutcomePayload.class);
    }

    @KafkaListener(
        id = "notifications-trade-events",
        topics = "trade-events",
        groupId = "${notifications.consumer.group-id:notifications}",
        containerFactory = NotificationConsumerConfig.CONTAINER_FACTORY,
        autoStartup = "${notifications.consumer.auto-startup:true}"
    )
    public void onTradeEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaEnvelope<TradeOutcomePayload> envelope = parse(record.value());
        String eventType = envelope.getEventType() == null ? "" : envelope.getEventType().toUpperCase(Locale.ROOT);

        if (!NOTIFIABLE_EVENT_TYPES.contains(eventType)) {
            log.debug("Ignoring {} eventId={}: not a notifiable outcome", eventType, envelope.getEventId());
            ack.acknowledge();
            return;
        }

        // the row is committed when this returns; only then is the offset committed
        boolean recorded = notificationService.recordTradeEvent(envelope);
        if (recorded) {
            log.info("Queued notification for {} eventId={} partition={} offset={}",
                eventType, envelope.getEventId(), record.partition(), record.offset());
        } else {
            log.info("Duplicate delivery of eventId={} ignored, nothing sent (partition={} offset={})",
                envelope.getEventId(), record.partition(), record.offset());
        }
        ack.acknowledge();
    }

    KafkaEnvelope<TradeOutcomePayload> parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidNotificationEventException("EMPTY_MESSAGE", "trade-events message has no value");
        }
        try {
            String json = value.trim();
            // the Trade Executor's JsonSerializer wraps its already-serialised JSON in a JSON
            // string ("{\"eventId\":...}"); unwrap once, as the executor's own deserializer does
            if (json.startsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            KafkaEnvelope<TradeOutcomePayload> envelope = objectMapper.readValue(json, envelopeType);
            if (envelope == null) {
                throw new InvalidNotificationEventException("MALFORMED_JSON", "trade-events message is null JSON");
            }
            return envelope;
        } catch (JsonProcessingException e) {
            throw new InvalidNotificationEventException("MALFORMED_JSON", "trade-events message is not valid JSON", e);
        }
    }
}
