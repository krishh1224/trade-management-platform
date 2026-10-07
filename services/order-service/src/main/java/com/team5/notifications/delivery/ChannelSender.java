package com.team5.notifications.delivery;

import com.team5.notifications.domain.Notification;
import com.team5.preferences.AlertChannel;

// One implementation per outbound channel. The destination is fixed by the implementation and
// the account, never supplied by a caller.
public interface ChannelSender {

    AlertChannel channel();

    // Throws when the channel refused the message; returning normally means it was handed over
    void send(Notification notification);
}
