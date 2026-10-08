package com.team5.notifications.domain;

// One browser a customer enabled push notifications on
public record PushSubscription(
    Long subscriptionId,
    long accountId,
    String endpoint,
    String p256dh,
    String auth
) {
}
