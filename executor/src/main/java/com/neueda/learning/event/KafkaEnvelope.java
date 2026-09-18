package com.neueda.learning.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Common envelope for all messages on orders, trade-events and market-data topics.
 * Imported from trade-api contract.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEnvelope<T> {

    @JsonProperty("eventId")
    private UUID eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventTime")
    private OffsetDateTime eventTime;

    @JsonProperty("source")
    private String source;

    @JsonProperty("schemaVersion")
    private int schemaVersion;

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

    @Override
    public String toString() {
        return "KafkaEnvelope{" +
                "eventId=" + eventId +
                ", eventType='" + eventType + '\'' +
                ", eventTime=" + eventTime +
                ", source='" + source + '\'' +
                ", schemaVersion=" + schemaVersion +
                ", payload=" + payload +
                '}';
    }
}
