package com.team5.watchlists.consumer;

import static com.team5.notifications.api.AlertDirection.ABOVE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.team5.notifications.consumer.TradeEventNotificationListener;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.service.MessageFormatter;
import com.team5.notifications.service.NotificationServiceImpl;
import com.team5.watchlists.InMemoryAlertStore;
import com.team5.watchlists.domain.MarketQuote;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.PriceAlertStatus;
import com.team5.watchlists.exception.InvalidQuoteException;
import com.team5.watchlists.service.PriceAlertEvaluator;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

class MarketDataQuoteListenerTest {

    private InMemoryAlertStore store;
    private InMemoryNotificationMapper notifications;
    private MarketDataQuoteListener listener;

    @BeforeEach
    void setUp() {
        store = new InMemoryAlertStore();
        notifications = new InMemoryNotificationMapper();
        PriceAlertEvaluator evaluator = new PriceAlertEvaluator(store.quoteMapper, store.alertMapper,
            new NotificationServiceImpl(notifications, new MessageFormatter(), new AccountAccessAuthorizer()));
        listener = new MarketDataQuoteListener(evaluator);
    }

    // exactly what the executor's MarketDataPublisher builds: the platform envelope, bid and ask only
    private static String executorQuote(String symbol, String bid, String ask, String eventTime) {
        return """
            {"eventId":"%s","eventType":"QUOTE","eventTime":"%s","source":"executor","schemaVersion":1,
             "payload":{"symbol":"%s","bid":%s,"ask":%s}}
            """.formatted(UUID.randomUUID(), eventTime, symbol, bid, ask);
    }

    @Test
    void executorQuotes_crossingAThreshold_triggerTheAlertAndCommitTheOffset() throws Exception {
        PriceAlertRow alert = store.addActive(101L, "INFY.NS", ABOVE, "1500.00");
        Acknowledgment ack = mock(Acknowledgment.class);
        ObjectMapper json = new ObjectMapper();

        // double-encoded, as a JsonSerializer wraps an already-serialised string; and plain
        listener.onQuote(record("INFY.NS", json.writeValueAsString(
            executorQuote("INFY.NS", "1489.90", "1490.10", "2026-10-07T09:00:00Z"))), ack);
        listener.onQuote(record("INFY.NS",
            executorQuote("INFY.NS", "1502.20", "1502.50", "2026-10-07T09:01:00Z")), ack);

        assertEquals(PriceAlertStatus.TRIGGERED, store.alert(alert.getAlertId()).getStatus());
        // the bid/ask midpoint to 4 places, as the executor stores its own snapshot
        assertEquals(0, new BigDecimal("1502.3500").compareTo(store.alert(alert.getAlertId()).getTriggerPrice()));
        assertEquals(NotificationType.PRICE_ALERT, notifications.rows().get(0).getType());
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void replayedQuote_isAcknowledgedAndSendsNothingTwice() {
        store.addActive(101L, "INFY.NS", ABOVE, "1500.00");
        Acknowledgment ack = mock(Acknowledgment.class);
        String below = executorQuote("INFY.NS", "1490", "1490", "2026-10-07T09:00:00Z");
        String above = executorQuote("INFY.NS", "1510", "1510", "2026-10-07T09:01:00Z");

        listener.onQuote(record("INFY.NS", below), ack);
        listener.onQuote(record("INFY.NS", above), ack);
        listener.onQuote(record("INFY.NS", above), ack);   // redelivery
        listener.onQuote(record("INFY.NS", below), ack);   // replay from an earlier offset
        listener.onQuote(record("INFY.NS", above), ack);

        assertEquals(1, notifications.rows().size());
        // every delivery is acknowledged, duplicates included, so a replay never blocks the partition
        verify(ack, times(5)).acknowledge();
    }

    @Test
    void quoteWithAPrice_usesItRatherThanTheMidpoint() {
        MarketQuote quote = listener.toQuote(listener.parse("""
            {"eventId":"%s","eventType":"QUOTE","eventTime":"2026-10-07T09:00:00.123456789Z",
             "payload":{"symbol":"AAPL","price":187.25,"bid":187.00,"ask":187.40}}
            """.formatted(UUID.randomUUID())), "AAPL");

        assertEquals(new BigDecimal("187.25"), quote.price());
        // TIMESTAMPTZ keeps microseconds; a redelivery must compare equal to what was stored
        assertEquals(OffsetDateTime.parse("2026-10-07T09:00:00.123456Z"), quote.quotedAt());
    }

    @Test
    void symbolFallsBackToTheMessageKey() {
        MarketQuote quote = listener.toQuote(listener.parse("""
            {"eventId":"%s","eventType":"QUOTE","eventTime":"2026-10-07T09:00:00Z","payload":{"bid":10,"ask":12}}
            """.formatted(UUID.randomUUID())), "TCS.NS");

        assertEquals("TCS.NS", quote.symbol());
        assertEquals(0, new BigDecimal("11").compareTo(quote.price()));
    }

    @Test
    void nonQuoteEvent_isAcknowledgedAndIgnored() {
        PriceAlertEvaluator evaluator = mock(PriceAlertEvaluator.class);
        MarketDataQuoteListener ignoring = new MarketDataQuoteListener(evaluator);
        Acknowledgment ack = mock(Acknowledgment.class);

        ignoring.onQuote(record("INFY.NS", """
            {"eventId":"%s","eventType":"MARKET_STATUS","eventTime":"2026-10-07T09:00:00Z","payload":{}}
            """.formatted(UUID.randomUUID())), ack);

        verifyNoInteractions(evaluator);
        verify(ack).acknowledge();
    }

    @Test
    void messagesThatCanNeverBeEvaluated_areNotRetryable_andNotAcknowledged() {
        Acknowledgment ack = mock(Acknowledgment.class);
        String[][] cases = {
            {"not json", "MALFORMED_JSON"},
            {"", "EMPTY_MESSAGE"},
            {"{\"eventType\":\"QUOTE\",\"eventTime\":\"2026-10-07T09:00:00Z\"}", "MISSING_PAYLOAD"},
            {"{\"eventType\":\"QUOTE\",\"eventTime\":\"2026-10-07T09:00:00Z\",\"payload\":{\"symbol\":\"INFY.NS\"}}", "MISSING_PRICE"},
            {"{\"eventType\":\"QUOTE\",\"eventTime\":\"2026-10-07T09:00:00Z\",\"payload\":{\"symbol\":\"INFY.NS\",\"bid\":0,\"ask\":10}}", "MISSING_PRICE"},
            {"{\"eventType\":\"QUOTE\",\"payload\":{\"symbol\":\"INFY.NS\",\"price\":10}}", "MISSING_TIME"},
        };
        for (String[] c : cases) {
            InvalidQuoteException e = assertThrows(InvalidQuoteException.class,
                () -> listener.onQuote(record(null, c[0]), ack), c[0]);
            assertEquals(c[1], e.getFailureReason(), c[0]);
        }
        verify(ack, never()).acknowledge();
    }

    @Test
    void offsetIsNotCommittedWhenTheQuoteCannotBeProcessed() {
        PriceAlertEvaluator failing = mock(PriceAlertEvaluator.class);
        when(failing.evaluate(any())).thenThrow(new DataAccessResourceFailureException("database down"));
        MarketDataQuoteListener failingListener = new MarketDataQuoteListener(failing);
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThrows(RuntimeException.class, () -> failingListener.onQuote(
            record("INFY.NS", executorQuote("INFY.NS", "1", "2", "2026-10-07T09:00:00Z")), ack));

        // not acknowledged: the container's error handler retries, then parks it on market-data.DLT
        verify(ack, never()).acknowledge();
    }

    // Watchlists consumes market-data only, in a consumer group of its own
    @Test
    void listenerReadsMarketDataOnly_inItsOwnConsumerGroup() throws Exception {
        KafkaListener watchlists = MarketDataQuoteListener.class
            .getMethod("onQuote", ConsumerRecord.class, Acknowledgment.class)
            .getAnnotation(KafkaListener.class);

        assertArrayEquals(new String[] {"market-data"}, watchlists.topics());
        assertEquals(0, watchlists.topicPattern().length());
        assertEquals(0, watchlists.topicPartitions().length);
        assertEquals("${watchlists.consumer.group-id:watchlists-market-data}", watchlists.groupId());
        assertEquals("watchlistMarketDataContainerFactory", watchlists.containerFactory());

        KafkaListener notificationsListener = TradeEventNotificationListener.class
            .getMethod("onTradeEvent", ConsumerRecord.class, Acknowledgment.class)
            .getAnnotation(KafkaListener.class);
        assertNotEquals(notificationsListener.groupId(), watchlists.groupId());
        assertNotEquals(notificationsListener.id(), watchlists.id());
    }

    @Test
    void noListenerInTheModuleSubscribesToOrdersOrTradeEvents() {
        for (Class<?> type : new Class<?>[] {MarketDataQuoteListener.class, WatchlistConsumerConfig.class}) {
            for (Method method : type.getDeclaredMethods()) {
                KafkaListener listenerAnnotation = method.getAnnotation(KafkaListener.class);
                if (listenerAnnotation == null) {
                    continue;
                }
                assertFalse(Arrays.asList(listenerAnnotation.topics()).contains("orders"));
                assertFalse(Arrays.asList(listenerAnnotation.topics()).contains("trade-events"));
                assertTrue(listenerAnnotation.topicPattern().isEmpty());
            }
        }
    }

    private static ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("market-data", 0, 0L, key, value);
    }
}
