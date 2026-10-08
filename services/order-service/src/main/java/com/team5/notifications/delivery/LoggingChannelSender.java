package com.team5.notifications.delivery;

import com.team5.notifications.domain.Notification;
import com.team5.preferences.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Stub for a channel we cannot provision on the course infrastructure. The routing decision that
// chose this channel is real and recorded; only the provider call is replaced by a log line.
// Logs the account and notification ids, never an address or the message body.
abstract class LoggingChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingChannelSender.class);

    private final AlertChannel channel;

    protected LoggingChannelSender(AlertChannel channel) {
        this.channel = channel;
    }

    @Override
    public AlertChannel channel() {
        return channel;
    }

    @Override
    public void send(Notification notification) {
        log.info("[{} stub] delivered notification {} ({}) to account {}",
            channel, notification.getNotificationId(), notification.getType(), notification.getAccountId());
    }
}
