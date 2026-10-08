package com.team5.notifications.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.OrderIdentifiers;
import com.team5.kafka.KafkaEnvelope;
import com.team5.notifications.api.AlertDelivery;
import com.team5.notifications.api.PriceAlert;
import com.team5.notifications.consumer.TradeOutcomePayload;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.exception.InvalidNotificationEventException;
import com.team5.notifications.exception.NotificationNotFoundException;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.notifications.mapper.StoredOrder;
import com.team5.notifications.web.NotificationResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService, AlertDelivery {

    private final NotificationMapper notificationMapper;
    private final MessageFormatter messageFormatter;
    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final Clock clock;

    @Autowired
    public NotificationServiceImpl(
        NotificationMapper notificationMapper,
        MessageFormatter messageFormatter,
        AccountAccessAuthorizer accountAccessAuthorizer
    ) {
        this(notificationMapper, messageFormatter, accountAccessAuthorizer, Clock.systemUTC());
    }

    NotificationServiceImpl(
        NotificationMapper notificationMapper,
        MessageFormatter messageFormatter,
        AccountAccessAuthorizer accountAccessAuthorizer,
        Clock clock
    ) {
        this.notificationMapper = notificationMapper;
        this.messageFormatter = messageFormatter;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean recordTradeEvent(KafkaEnvelope<TradeOutcomePayload> envelope) {
        if (envelope == null || envelope.getEventId() == null) {
            // without an eventId there is nothing to be idempotent on
            throw new InvalidNotificationEventException("MISSING_EVENT_ID", "trade event has no eventId");
        }
        TradeOutcomePayload payload = envelope.getPayload();
        if (payload == null || payload.getAccountId() == null || payload.getAccountId() <= 0) {
            throw new InvalidNotificationEventException(
                "MISSING_ACCOUNT", "trade event " + envelope.getEventId() + " has no account");
        }
        NotificationType type = toTradeNotificationType(envelope.getEventType());
        long accountId = payload.getAccountId();
        Long orderId = payload.persistentOrderId();

        OrderFacts facts = new OrderFacts(
            payload.getSymbol(),
            payload.getSide(),
            payload.getQuantity(),
            payload.getPrice(),
            payload.getExecutedPrice(),
            payload.reasonCode()
        );
        if (!facts.describesOrder() && orderId != null) {
            // an executor rejection names only the order; a customer needs to know which one failed
            StoredOrder stored = notificationMapper.findOrderForAccount(orderId, accountId);
            if (stored != null) {
                facts = facts.withOrderDetails(
                    new OrderFacts(stored.symbol(), stored.side(), stored.quantity(), stored.price(), null, null));
            }
        }

        String message = messageFormatter.forTradeEvent(type, facts);
        return insert(envelope.getEventId(), accountId, type, orderId, message);
    }

    @Override
    @Transactional
    public boolean recordCancelledOrder(long accountId, long persistentOrderId, String symbol, String side,
                                        Integer quantity, BigDecimal price) {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId(String.valueOf(persistentOrderId));
        payload.setAccountId(accountId);
        payload.setSymbol(symbol);
        payload.setSide(side);
        payload.setQuantity(quantity);
        payload.setPrice(price);

        // the same envelope the executor's ORDER_CANCELLED produces, down to the eventId
        return recordTradeEvent(new KafkaEnvelope<>(
            OrderIdentifiers.cancelledEventId(persistentOrderId),
            "ORDER_CANCELLED",
            OffsetDateTime.now(clock),
            "trade-api",
            1,
            payload
        ));
    }

    @Override
    @Transactional
    public boolean queueOrderHistoryExport(long authenticatedAccountId, long accountId) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        return insert(
            UUID.randomUUID(),
            accountId,
            NotificationType.ORDER_HISTORY_EXPORT,
            null,
            "Your order history PDF report is ready and attached to this email."
        );
    }

    @Override
    @Transactional
    public boolean deliver(PriceAlert alert) {
        if (alert == null || alert.alertId() == null || alert.accountId() <= 0
            || alert.threshold() == null || alert.triggerPrice() == null || alert.direction() == null) {
            throw new IllegalArgumentException(
                "price alert needs alertId, accountId, threshold, triggerPrice and direction");
        }
        String message = messageFormatter.forPriceAlert(alert);
        return insert(alert.alertId(), alert.accountId(), NotificationType.PRICE_ALERT, null, message);
    }

    @Override
    public List<NotificationResponse> listNotifications(
        long authenticatedAccountId,
        long accountId,
        NotificationStatus status,
        int limit
    ) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        return notificationMapper.findByAccount(accountId, status, limit).stream()
            .map(NotificationResponse::from)
            .toList();
    }

    @Override
    public NotificationResponse getNotification(long authenticatedAccountId, long accountId, long notificationId) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        return NotificationResponse.from(findOwned(accountId, notificationId));
    }

    @Override
    @Transactional
    public NotificationResponse markRead(long authenticatedAccountId, long accountId, long notificationId) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        findOwned(accountId, notificationId);
        notificationMapper.markRead(notificationId, accountId, OffsetDateTime.now(clock));
        return NotificationResponse.from(findOwned(accountId, notificationId));
    }

    private boolean insert(
        UUID eventId,
        long accountId,
        NotificationType type,
        Long orderId,
        String message
    ) {
        Notification notification = new Notification();
        notification.setEventId(eventId);
        notification.setAccountId(accountId);
        notification.setType(type);
        notification.setOrderId(orderId);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setCreatedAt(OffsetDateTime.now(clock));

        // the unique constraint on event_id decides, not a read-then-write check that two
        // concurrent deliveries of the same event could both pass
        return notificationMapper.insertIfAbsent(notification) == 1;
    }

    private Notification findOwned(long accountId, long notificationId) {
        Notification notification = notificationMapper.findByIdForAccount(notificationId, accountId);
        if (notification == null) {
            throw new NotificationNotFoundException();
        }
        return notification;
    }

    private NotificationType toTradeNotificationType(String eventType) {
        String normalised = eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "ORDER_FILLED" -> NotificationType.ORDER_FILLED;
            case "ORDER_REJECTED" -> NotificationType.ORDER_REJECTED;
            case "ORDER_CANCELLED" -> NotificationType.ORDER_CANCELLED;
            default -> throw new InvalidNotificationEventException(
                "UNSUPPORTED_EVENT_TYPE", "event type " + eventType + " does not produce a notification");
        };
    }
}
