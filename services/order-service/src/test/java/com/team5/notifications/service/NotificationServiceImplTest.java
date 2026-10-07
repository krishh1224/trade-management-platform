package com.team5.notifications.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.OrderIdentifiers;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.kafka.KafkaEnvelope;
import com.team5.notifications.InMemoryNotificationMapper;
import com.team5.notifications.api.AlertDirection;
import com.team5.notifications.api.PriceAlert;
import com.team5.notifications.consumer.TradeOutcomePayload;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.exception.InvalidNotificationEventException;
import com.team5.notifications.exception.NotificationNotFoundException;
import com.team5.notifications.mapper.StoredOrder;
import com.team5.notifications.web.NotificationResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceImplTest {

    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryNotificationMapper mapper;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryNotificationMapper();
        service = new NotificationServiceImpl(mapper, new MessageFormatter(), new AccountAccessAuthorizer(), CLOCK);
    }

    // Execution path 1: a filled order produces a notification
    @Test
    void filledOrder_producesQueuedNotification() {
        KafkaEnvelope<TradeOutcomePayload> event = envelope("ORDER_FILLED", filledPayload());

        boolean recorded = service.recordTradeEvent(event);

        assertTrue(recorded);
        Notification row = onlyRow();
        assertEquals(event.getEventId(), row.getEventId());
        assertEquals(OWNER, row.getAccountId());
        assertEquals(NotificationType.ORDER_FILLED, row.getType());
        assertEquals(NotificationStatus.QUEUED, row.getStatus());
        assertEquals(9001L, row.getOrderId());
        assertNull(row.getChannel(), "channel is resolved at send time, not when recorded");
        assertEquals("Your BUY order for 10 AAPL was filled at 187.25.", row.getMessage());
    }

    // Execution path 2: a rejected order produces a notification
    @Test
    void rejectedOrder_producesNotificationNamingTheOrderAndTheReason() {
        // the executor's ORDER_REJECTED carries only orderId, accountId and rejectionReason
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId("9002");
        payload.setAccountId(OWNER);
        payload.setRejectionReason("INSUFFICIENT_FUNDS");
        mapper.storeOrder(9002L, OWNER, new StoredOrder("TSLA", "BUY", 5, new BigDecimal("250.00")));

        boolean recorded = service.recordTradeEvent(envelope("ORDER_REJECTED", payload));

        assertTrue(recorded);
        Notification row = onlyRow();
        assertEquals(NotificationType.ORDER_REJECTED, row.getType());
        assertEquals(NotificationStatus.QUEUED, row.getStatus());
        assertEquals("Your BUY order for 5 TSLA was rejected (insufficient funds).", row.getMessage());
    }

    @Test
    void rejectedOrder_withUnknownOrder_stillProducesNotification() {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId("9999");
        payload.setAccountId(OWNER);
        payload.setRejectionReason("PRICE_NOT_MET");

        service.recordTradeEvent(envelope("ORDER_REJECTED", payload));

        assertEquals("Your order was rejected (price not met).", onlyRow().getMessage());
    }

    @Test
    void rejectedOrder_neverPullsInAnotherAccountsOrder() {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId("9003");
        payload.setAccountId(OWNER);
        payload.setRejectionReason("INSUFFICIENT_FUNDS");
        mapper.storeOrder(9003L, OTHER_ACCOUNT, new StoredOrder("NVDA", "SELL", 50, new BigDecimal("900.00")));

        service.recordTradeEvent(envelope("ORDER_REJECTED", payload));

        assertEquals("Your order was rejected (insufficient funds).", onlyRow().getMessage());
    }

    @Test
    void cancelledOrder_producesNotification() {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId("9004");
        payload.setAccountId(OWNER);
        payload.setSymbol("INFY.NS");
        payload.setSide("SELL");
        payload.setQuantity(3);
        payload.setPrice(new BigDecimal("1500"));

        service.recordTradeEvent(envelope("ORDER_CANCELLED", payload));

        Notification row = onlyRow();
        assertEquals(NotificationType.ORDER_CANCELLED, row.getType());
        assertEquals("Your SELL order for 3 INFY.NS was cancelled.", row.getMessage());
    }

    // Option B: the trade-api records a cancel at once; the executor's ORDER_CANCELLED arrives later
    @Test
    void cancelRecordedByTradeApi_thenExecutorEvent_givesOneNotification() {
        assertTrue(service.recordCancelledOrder(OWNER, 9004L, "INFY.NS", "SELL", 3, new BigDecimal("1500")));

        boolean executorRecorded = service.recordTradeEvent(executorCancel(9004L));

        assertFalse(executorRecorded, "the executor's announcement must be a no-op");
        Notification row = onlyRow();
        assertEquals(NotificationType.ORDER_CANCELLED, row.getType());
        assertEquals(OrderIdentifiers.cancelledEventId(9004L), row.getEventId());
        assertEquals("Your SELL order for 3 INFY.NS was cancelled.", row.getMessage());
    }

    @Test
    void executorEventFirst_thenTradeApi_givesOneNotification() {
        assertTrue(service.recordTradeEvent(executorCancel(9005L)));

        assertFalse(service.recordCancelledOrder(OWNER, 9005L, "INFY.NS", "SELL", 3, new BigDecimal("1500")));

        assertEquals(1, mapper.rows().size());
    }

    @Test
    void differentCancelledOrders_getTheirOwnNotifications() {
        service.recordCancelledOrder(OWNER, 9006L, "INFY.NS", "BUY", 1, BigDecimal.ONE);
        service.recordCancelledOrder(OWNER, 9007L, "INFY.NS", "BUY", 1, BigDecimal.ONE);

        assertEquals(2, mapper.rows().size());
    }

    @Test
    void cancelledEventId_isPinnedAndMatchesTheExecutor() {
        // TradeEventPublisherTest.cancelledEventId_matchesTheTradeApi asserts the same value
        assertEquals(UUID.fromString("aaeeaf6d-1bdd-353d-9c86-c4e227977968"), OrderIdentifiers.cancelledEventId(530L));
    }

    // what the executor's TradeEventPublisher.publishOrderCancelled puts on trade-events
    private static KafkaEnvelope<TradeOutcomePayload> executorCancel(long orderId) {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId(String.valueOf(orderId));
        payload.setAccountId(OWNER);
        payload.setSymbol("INFY.NS");
        payload.setSide("SELL");
        payload.setQuantity(3);
        payload.setPrice(new BigDecimal("1500"));
        return new KafkaEnvelope<>(OrderIdentifiers.cancelledEventId(orderId), "ORDER_CANCELLED",
            OffsetDateTime.now(CLOCK), "executor", 1, payload);
    }

    @Test
    void replayedEvent_isRecordedOnce() {
        KafkaEnvelope<TradeOutcomePayload> event = envelope("ORDER_FILLED", filledPayload());

        assertTrue(service.recordTradeEvent(event));
        assertFalse(service.recordTradeEvent(event));

        assertEquals(1, mapper.rows().size());
    }

    @Test
    void eventWithoutEventId_isRejectedAsUnprocessable() {
        KafkaEnvelope<TradeOutcomePayload> event = envelope("ORDER_FILLED", filledPayload());
        event.setEventId(null);

        InvalidNotificationEventException error =
            assertThrows(InvalidNotificationEventException.class, () -> service.recordTradeEvent(event));
        assertEquals("MISSING_EVENT_ID", error.getFailureReason());
        assertTrue(mapper.rows().isEmpty());
    }

    @Test
    void eventWithoutAccount_isRejectedAsUnprocessable() {
        TradeOutcomePayload payload = filledPayload();
        payload.setAccountId(null);

        InvalidNotificationEventException error = assertThrows(InvalidNotificationEventException.class,
            () -> service.recordTradeEvent(envelope("ORDER_FILLED", payload)));
        assertEquals("MISSING_ACCOUNT", error.getFailureReason());
    }

    @Test
    void priceAlert_isRecordedOnceForTheOwningAccount() {
        PriceAlert alert = new PriceAlert(UUID.randomUUID(), OWNER, "AAPL", new BigDecimal("190"),
            new BigDecimal("190.35"), AlertDirection.ABOVE, OffsetDateTime.now(CLOCK));

        assertTrue(service.deliver(alert));
        assertFalse(service.deliver(alert), "the same alertId must not be delivered twice");

        Notification row = onlyRow();
        assertEquals(NotificationType.PRICE_ALERT, row.getType());
        assertEquals(OWNER, row.getAccountId());
        assertEquals("AAPL rose above your alert price of 190.00. Last price 190.35.", row.getMessage());
    }

    // Execution path 5: notification history is refused to an account that does not own it
    @Test
    void history_isRefusedToAnAccountThatDoesNotOwnIt() {
        service.recordTradeEvent(envelope("ORDER_FILLED", filledPayload()));

        assertThrows(AccountAccessDeniedException.class,
            () -> service.listNotifications(OTHER_ACCOUNT, OWNER, null, 50));
    }

    @Test
    void history_isReturnedToItsOwner() {
        service.recordTradeEvent(envelope("ORDER_FILLED", filledPayload()));

        List<NotificationResponse> history = service.listNotifications(OWNER, OWNER, null, 50);

        assertEquals(1, history.size());
        assertEquals(NotificationType.ORDER_FILLED, history.get(0).type());
    }

    @Test
    void singleNotification_ofAnotherAccount_isNotFound() {
        service.recordTradeEvent(envelope("ORDER_FILLED", filledPayload()));
        long id = onlyRow().getNotificationId();

        // the other account addresses its own history but names the owner's notification id
        assertThrows(NotificationNotFoundException.class,
            () -> service.getNotification(OTHER_ACCOUNT, OTHER_ACCOUNT, id));
    }

    @Test
    void markRead_setsReadAtOnce() {
        service.recordTradeEvent(envelope("ORDER_FILLED", filledPayload()));
        long id = onlyRow().getNotificationId();

        NotificationResponse first = service.markRead(OWNER, OWNER, id);
        NotificationResponse second = service.markRead(OWNER, OWNER, id);

        assertNotNull(first.readAt());
        assertEquals(first.readAt(), second.readAt());
    }

    private Notification onlyRow() {
        List<Notification> rows = mapper.rows();
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private static TradeOutcomePayload filledPayload() {
        TradeOutcomePayload payload = new TradeOutcomePayload();
        payload.setOrderId("9001");
        payload.setAccountId(OWNER);
        payload.setSymbol("AAPL");
        payload.setSide("BUY");
        payload.setQuantity(10);
        payload.setExecutedPrice(new BigDecimal("187.25"));
        return payload;
    }

    private static KafkaEnvelope<TradeOutcomePayload> envelope(String eventType, TradeOutcomePayload payload) {
        return new KafkaEnvelope<>(UUID.randomUUID(), eventType, OffsetDateTime.now(CLOCK), "trade-executor", 1, payload);
    }
}
