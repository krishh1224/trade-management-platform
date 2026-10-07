package com.team5.notifications.domain;

import com.team5.preferences.AlertChannel;
import java.time.OffsetDateTime;
import java.util.UUID;

// One row of the notifications table: the delivery ledger and the customer's inbox entry
public class Notification {

    private Long notificationId;
    private UUID eventId; // idempotency key: Kafka eventId, or watchlist alertId
    private long accountId;
    private NotificationType type;
    private Long orderId; // orders.order_id; null for a price alert
    private String message;
    private AlertChannel channel; // resolved at send time, null until then
    private NotificationStatus status;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime readAt;

    public Notification() {
    }

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public AlertChannel getChannel() { return channel; }
    public void setChannel(AlertChannel channel) { this.channel = channel; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }

    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
}
