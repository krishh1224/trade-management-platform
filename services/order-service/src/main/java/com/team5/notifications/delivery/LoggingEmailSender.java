package com.team5.notifications.delivery;

import com.team5.preferences.AlertChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Default EMAIL channel: logs instead of sending. Replaced by SmtpEmailSender when
// notifications.email.enabled=true, so machines without mail credentials keep working.
@Component
@ConditionalOnProperty(prefix = "notifications.email", name = "enabled", havingValue = "false", matchIfMissing = true)
class LoggingEmailSender extends LoggingChannelSender {

    LoggingEmailSender() {
        super(AlertChannel.EMAIL);
    }
}
