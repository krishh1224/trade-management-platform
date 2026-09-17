package com.neueda.learning.event;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka event envelope for ORDER_PLACED events.
 * Tolerates additional unknown JSON fields via JsonAnySetter.
 */
public class OrderPlacedEvent {
    private String eventType;
    private Long orderId;
    private Long accountId;
    private Long timestamp;

    // Capture any unknown fields
    private Map<String, Object> additionalProperties = new HashMap<>();

    public OrderPlacedEvent() {
    }

    public OrderPlacedEvent(String eventType, Long orderId, Long accountId, Long timestamp) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.accountId = accountId;
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @Override
    public String toString() {
        return "OrderPlacedEvent{" +
                "eventType='" + eventType + '\'' +
                ", orderId=" + orderId +
                ", accountId=" + accountId +
                ", timestamp=" + timestamp +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}

