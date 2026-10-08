package com.team5.notifications.delivery;

import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.preferences.AlertChannel;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Writes the outcome of a send onto the notification row: status, the channel it was resolved
// to, and when it went out or why it failed.
@Component
public class DeliveryLedger {

    private static final int MAX_ERROR_LENGTH = 500;

    private final NotificationMapper notificationMapper;
    private final Clock clock;

    @Autowired
    public DeliveryLedger(NotificationMapper notificationMapper) {
        this(notificationMapper, Clock.systemUTC());
    }

    DeliveryLedger(NotificationMapper notificationMapper, Clock clock) {
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    public void markSent(Notification notification, AlertChannel channel) {
        notificationMapper.updateDeliveryState(
            notification.getNotificationId(), NotificationStatus.SENT, channel, null, OffsetDateTime.now(clock));
    }

    public void markFailed(Notification notification, AlertChannel channel, String error) {
        notificationMapper.updateDeliveryState(
            notification.getNotificationId(), NotificationStatus.FAILED, channel, truncate(error), null);
    }

    // stored preference is NONE: recorded as resolved, nothing goes outbound
    public void markSkipped(Notification notification) {
        notificationMapper.updateDeliveryState(
            notification.getNotificationId(), NotificationStatus.SKIPPED, AlertChannel.NONE, null, null);
    }

    // no preference stored yet: no channel; the dispatcher looks again next pass
    public void markHeld(Notification notification) {
        notificationMapper.updateDeliveryState(
            notification.getNotificationId(), NotificationStatus.HELD, null, null, null);
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
