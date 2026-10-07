package com.team5.notifications.delivery;

import com.team5.preferences.AlertChannel;
import org.springframework.stereotype.Component;

// Replace with a client for an SMS gateway once one can be provisioned
@Component
class LoggingSmsSender extends LoggingChannelSender {

    LoggingSmsSender() {
        super(AlertChannel.SMS);
    }
}
