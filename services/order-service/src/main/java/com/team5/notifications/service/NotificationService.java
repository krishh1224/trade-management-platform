package com.team5.notifications.service;

import com.team5.kafka.KafkaEnvelope;
import com.team5.notifications.consumer.TradeOutcomePayload;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.web.NotificationResponse;
import java.math.BigDecimal;
import java.util.List;

public interface NotificationService {

    // Records a FILLED / REJECTED / CANCELLED event as a QUEUED notification.
    // Returns false when the eventId was already recorded: a replay is a no-op.
    boolean recordTradeEvent(KafkaEnvelope<TradeOutcomePayload> envelope);

    // Records a cancel the trade-api has just committed, without waiting for the executor to announce
    // it on trade-events. Uses the same eventId the executor will, so whichever arrives second is a
    // no-op: one notification per cancelled order. Returns false when it was already recorded.
    boolean recordCancelledOrder(long accountId, long persistentOrderId, String symbol, String side,
                                 Integer quantity, BigDecimal price);

    List<NotificationResponse> listNotifications(
        long authenticatedAccountId,
        long accountId,
        NotificationStatus status,
        int limit
    );

    NotificationResponse getNotification(long authenticatedAccountId, long accountId, long notificationId);

    NotificationResponse markRead(long authenticatedAccountId, long accountId, long notificationId);
}
