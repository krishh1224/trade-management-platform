package com.team5.notifications.web;

import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.preferences.AlertChannel;
import java.time.OffsetDateTime;

public record NotificationResponse(
    long notificationId,
    NotificationType type,
    Long orderId,
    String title,
    String message,
    NotificationStatus status,
    AlertChannel channel,
    OffsetDateTime createdAt,
    OffsetDateTime sentAt,
    OffsetDateTime readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getNotificationId(),
            notification.getType(),
            notification.getOrderId(),
            notification.getType().title(),
            notification.getMessage(),
            notification.getStatus(),
            notification.getChannel(),
            notification.getCreatedAt(),
            notification.getSentAt(),
            notification.getReadAt()
        );
    }
}
