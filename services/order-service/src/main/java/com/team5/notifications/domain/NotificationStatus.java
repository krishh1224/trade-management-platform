package com.team5.notifications.domain;

// Delivery state, tracked separately from the Kafka offset
public enum NotificationStatus {
    QUEUED,  // recorded, waiting for the dispatcher
    HELD,    // no preference stored yet; re-checked on every dispatcher pass
    SENT,    // handed to the resolved channel
    FAILED,  // the resolved channel refused it
    SKIPPED  // the stored preference is NONE; stays in the inbox, nothing goes outbound
}
