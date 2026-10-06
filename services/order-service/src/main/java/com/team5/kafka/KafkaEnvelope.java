package com.team5.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

// Common envelope for all messages on orders, trade-events and market-data topics
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEnvelope<T> {

    @JsonProperty("eventId")
    private UUID eventId; // unique per message, used as idempotency key by consumers

    @JsonProperty("eventType")
    private String eventType; // ORDER_PLACED, ORDER_FILLED, ORDER_REJECTED, ORDER_CANCELLED, QUOTE

    @JsonProperty("eventTime")
    private OffsetDateTime eventTime; // when the producer created the event, not when consumed

    @JsonProperty("source")
    private String source; // trade-api, trade-executor, or market-poller

    @JsonProperty("schemaVersion")
    private int schemaVersion; // starts at 1, increment only on breaking changes

    @JsonProperty("payload")
    private T payload;

    public KafkaEnvelope() {
    }

    public KafkaEnvelope(UUID eventId, String eventType, OffsetDateTime eventTime,
                         String source, int schemaVersion, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.source = source;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public OffsetDateTime getEventTime() { return eventTime; }
    public void setEventTime(OffsetDateTime eventTime) { this.eventTime = eventTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
} 
    

