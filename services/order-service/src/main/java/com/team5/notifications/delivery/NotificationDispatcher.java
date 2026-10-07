package com.team5.notifications.delivery;

import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.preferences.AlertChannel;
import com.team5.preferences.CustomerPreferencesLookup;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Second half of the pipeline. The Kafka listener only records; this sends. A slow or broken
// provider therefore delays this loop, never the trade-events partition.
//
// The channel is resolved from Customer Preferences for every notification it sends, so a
// preference changed a second ago is the one used. Assumes a single Trade REST API instance:
// running several would need rows claimed with SELECT ... FOR UPDATE SKIP LOCKED first.
@Component
@ConditionalOnProperty(prefix = "notifications.dispatcher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationMapper notificationMapper;
    private final CustomerPreferencesLookup preferencesLookup;
    private final ChannelSenderRegistry senderRegistry;
    private final DeliveryLedger deliveryLedger;
    private final int batchSize;

    public NotificationDispatcher(
        NotificationMapper notificationMapper,
        CustomerPreferencesLookup preferencesLookup,
        ChannelSenderRegistry senderRegistry,
        DeliveryLedger deliveryLedger,
        @Value("${notifications.dispatcher.batch-size:50}") int batchSize
    ) {
        this.notificationMapper = notificationMapper;
        this.preferencesLookup = preferencesLookup;
        this.senderRegistry = senderRegistry;
        this.deliveryLedger = deliveryLedger;
        this.batchSize = batchSize;
    }

    @Scheduled(
        initialDelayString = "${notifications.dispatcher.interval-ms:5000}",
        fixedDelayString = "${notifications.dispatcher.interval-ms:5000}"
    )
    public void dispatchPending() {
        List<Notification> pending;
        try {
            pending = notificationMapper.findPending(batchSize);
        } catch (RuntimeException e) {
            log.error("Could not read pending notifications, will retry next pass: {}", e.getMessage());
            return;
        }
        for (Notification notification : pending) {
            try {
                dispatch(notification);
            } catch (RuntimeException e) {
                // left QUEUED/HELD, so it is picked up again on the next pass
                log.error("Dispatch of notification {} interrupted, will retry next pass: {}",
                    notification.getNotificationId(), e.getMessage());
            }
        }
    }

    void dispatch(Notification notification) {
        Optional<AlertChannel> resolved = preferencesLookup.findAlertChannel(notification.getAccountId());

        if (resolved.isEmpty()) {
            // decision: hold, not default. The message is not lost (it is in the inbox) and it
            // goes out on the next pass after the customer stores a preference.
            if (notification.getStatus() != NotificationStatus.HELD) {
                deliveryLedger.markHeld(notification);
                log.info("Notification {} held: account {} has no alert channel stored",
                    notification.getNotificationId(), notification.getAccountId());
            }
            return;
        }

        AlertChannel channel = resolved.get();
        if (channel == AlertChannel.NONE) {
            deliveryLedger.markSkipped(notification);
            return;
        }

        Optional<ChannelSender> sender = senderRegistry.forChannel(channel);
        if (sender.isEmpty()) {
            deliveryLedger.markFailed(notification, channel, "No sender configured for channel " + channel);
            return;
        }

        try {
            sender.get().send(notification);
        } catch (RuntimeException e) {
            deliveryLedger.markFailed(notification, channel, e.getMessage());
            log.warn("Notification {} failed on {}: {}", notification.getNotificationId(), channel, e.getMessage());
            return;
        }
        deliveryLedger.markSent(notification, channel);
    }
}
