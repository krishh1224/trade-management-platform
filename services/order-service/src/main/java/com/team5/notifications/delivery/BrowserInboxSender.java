package com.team5.notifications.delivery;

import com.team5.notifications.domain.Notification;
import com.team5.preferences.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// BROWSER is delivered in-app: the Angular inbox reads the notifications table through
// GET /api/v1/accounts/{id}/notifications, so the row being durable is the delivery.
@Component
class BrowserInboxSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(BrowserInboxSender.class);

    @Override
    public AlertChannel channel() {
        return AlertChannel.BROWSER;
    }

    @Override
    public void send(Notification notification) {
        log.info("[BROWSER] notification {} ({}) available in the inbox of account {}",
            notification.getNotificationId(), notification.getType(), notification.getAccountId());
    }
}
