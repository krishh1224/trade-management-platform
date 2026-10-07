package com.team5.notifications.delivery;

import com.team5.preferences.AlertChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Default PUSH channel: logs instead of sending. Replaced by WebPushSender when
// notifications.push.enabled=true, so machines without VAPID keys keep working.
@Component
@ConditionalOnProperty(prefix = "notifications.push", name = "enabled", havingValue = "false", matchIfMissing = true)
class LoggingPushSender extends LoggingChannelSender {

    LoggingPushSender() {
        super(AlertChannel.PUSH);
    }
}
