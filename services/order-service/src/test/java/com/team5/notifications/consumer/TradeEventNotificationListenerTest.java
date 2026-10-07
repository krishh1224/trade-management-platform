package com.team5.notifications.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team5.auth.AccountAccessAuthorizer;
import com.team5.notifications.InMemoryNotificationMapper;
import com.team5.notifications.delivery.ChannelSender;
import com.team5.notifications.delivery.ChannelSenderRegistry;
import com.team5.notifications.delivery.DeliveryLedger;
import com.team5.notifications.delivery.NotificationDispatcher;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.exception.InvalidNotificationEventException;
import com.team5.notifications.service.MessageFormatter;
import com.team5.notifications.service.NotificationService;
import com.team5.notifications.service.NotificationServiceImpl;
import com.team5.preferences.AlertChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;

class TradeEventNotificationListenerTest {

    private static final String EVENT_ID = "8a1f0c6e-5b5a-4c1e-9f0e-3c2b1a0d9e8f";

    // exactly what the Trade Executor's TradeEventPublisher builds for ORDER_FILLED
    private static final String EXECUTOR_FILLED_JSON = """
        {"eventId":"%s","eventType":"ORDER_FILLED","eventTime":"2026-10-07T09:00:00Z",
         "source":"executor","schemaVersion":1,
         "payload":{"orderId":9001,"accountId":101,"executedPrice":187.25,
                    "executedOn":"2026-10-07T09:00:00Z","quantity":10,"symbol":"AAPL","side":"BUY",
                    "fxRate":null,"settlementCurrency":null,"settlementAmount":null}}
        """.formatted(EVENT_ID);

    private InMemoryNotificationMapper mapper;
    private List<Long> emailed;
    private TradeEventNotificationListener listener;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryNotificationMapper();
        emailed = new ArrayList<>();
        NotificationService service =
            new NotificationServiceImpl(mapper, new MessageFormatter(), new AccountAccessAuthorizer());
        listener = new TradeEventNotificationListener(service);

        ChannelSender emailSender = new ChannelSender() {
            @Override
            public AlertChannel channel() {
                return AlertChannel.EMAIL;
            }

            @Override
            public void send(Notification notification) {
                emailed.add(notification.getNotificationId());
            }
        };
        dispatcher = new NotificationDispatcher(
            mapper,
            accountId -> Optional.of(AlertChannel.EMAIL),
            new ChannelSenderRegistry(List.of(emailSender)),
            new DeliveryLedger(mapper),
            50
        );
    }

    // Execution path 4: a replayed event does not send a second message
    @Test
    void replayedEvent_doesNotSendASecondMessage() throws Exception {
        String executorWireValue = new ObjectMapper().writeValueAsString(EXECUTOR_FILLED_JSON); // double-encoded
        Acknowledgment ack = mock(Acknowledgment.class);

        listener.onTradeEvent(record(executorWireValue, 0), ack);
        listener.onTradeEvent(record(executorWireValue, 0), ack); // redelivery before dispatch
        dispatcher.dispatchPending();
        listener.onTradeEvent(record(executorWireValue, 0), ack); // replay after delivery
        dispatcher.dispatchPending();

        assertEquals(1, mapper.rows().size(), "one notification for one eventId");
        assertEquals(1, emailed.size(), "the customer received exactly one message");
        assertEquals(NotificationStatus.SENT, mapper.rows().get(0).getStatus());
        // every delivery is acknowledged, duplicates included, so a replay never blocks the partition
        verify(ack, times(3)).acknowledge();
    }

    @Test
    void executorEvent_isParsedWhetherOrNotItIsDoubleEncoded() throws Exception {
        Acknowledgment ack = mock(Acknowledgment.class);

        listener.onTradeEvent(record(EXECUTOR_FILLED_JSON, 0), ack);

        Notification row = mapper.rows().get(0);
        assertEquals(NotificationType.ORDER_FILLED, row.getType());
        assertEquals(101L, row.getAccountId());
        assertEquals(9001L, row.getOrderId());
        assertEquals("Your BUY order for 10 AAPL was filled at 187.25.", row.getMessage());
        verify(ack).acknowledge();
    }

    @Test
    void executorCancelEvent_becomesACancelledNotification() throws Exception {
        // exactly what the executor's TradeEventPublisher.publishOrderCancelled builds, double-encoded
        String cancelled = """
            {"eventId":"0f4b3c2e-1d6a-3b8e-9c7f-5a2e1d0c9b8a","eventType":"ORDER_CANCELLED",
             "eventTime":"2026-10-07T11:00:00Z","source":"executor","schemaVersion":1,
             "payload":{"orderId":9004,"accountId":101,"symbol":"INFY.NS","side":"BUY","quantity":1,
                        "price":1100.00,"status":"CANCELLED","cancelledOn":"2026-10-07T11:00:00Z"}}
            """;
        Acknowledgment ack = mock(Acknowledgment.class);

        listener.onTradeEvent(record(new ObjectMapper().writeValueAsString(cancelled), 0), ack);

        Notification row = mapper.rows().get(0);
        assertEquals(NotificationType.ORDER_CANCELLED, row.getType());
        assertEquals(9004L, row.getOrderId());
        assertEquals("Your BUY order for 1 INFY.NS was cancelled.", row.getMessage());
        verify(ack).acknowledge();
    }

    @Test
    void offsetIsNotCommittedWhenTheNotificationCannotBeRecorded() {
        Acknowledgment ack = mock(Acknowledgment.class);
        NotificationService failing = mock(NotificationService.class);
        when(failing.recordTradeEvent(any()))
            .thenThrow(new DataAccessResourceFailureException("database down"));
        TradeEventNotificationListener failingListener = new TradeEventNotificationListener(failing);

        assertThrows(RuntimeException.class, () -> failingListener.onTradeEvent(record(EXECUTOR_FILLED_JSON, 0), ack));

        // not acknowledged: the container's error handler retries, and Kafka redelivers
        verify(ack, never()).acknowledge();
    }

    @Test
    void malformedMessage_isNotAcknowledgedAndIsMarkedForTheDeadLetterTopic() {
        Acknowledgment ack = mock(Acknowledgment.class);

        InvalidNotificationEventException error = assertThrows(InvalidNotificationEventException.class,
            () -> listener.onTradeEvent(record("{not json", 0), ack));

        assertEquals("MALFORMED_JSON", error.getFailureReason());
        verify(ack, never()).acknowledge();
        assertTrue(mapper.rows().isEmpty());
    }

    @Test
    void nonNotifiableEvent_isAcknowledgedWithoutRecording() {
        NotificationService service = mock(NotificationService.class);
        TradeEventNotificationListener quietListener = new TradeEventNotificationListener(service);
        Acknowledgment ack = mock(Acknowledgment.class);

        quietListener.onTradeEvent(record(EXECUTOR_FILLED_JSON.replace("ORDER_FILLED", "QUOTE"), 0), ack);

        verifyNoInteractions(service);
        verify(ack).acknowledge();
    }

    private static ConsumerRecord<String, String> record(String value, long offset) {
        return new ConsumerRecord<>("trade-events", 1, offset, "101", value);
    }
}
