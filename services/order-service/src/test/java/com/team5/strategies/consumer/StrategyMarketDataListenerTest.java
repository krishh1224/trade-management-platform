package com.team5.strategies.consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.engine.StrategyEngine;
import com.team5.strategies.engine.StrategyEngine.CycleResult;
import com.team5.strategies.engine.StrategyQuoteRecorder.Recorded;
import com.team5.strategies.exception.UnusableQuoteException;
import com.team5.watchlists.consumer.MarketDataQuoteListener;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

class StrategyMarketDataListenerTest {

    private StrategyEngine engine;
    private StrategyMarketDataListener listener;
    private Acknowledgment ack;

    @BeforeEach
    void setUp() {
        engine = mock(StrategyEngine.class);
        listener = new StrategyMarketDataListener(engine);
        ack = mock(Acknowledgment.class);
        when(engine.onQuote(any())).thenReturn(new CycleResult(Recorded.NEW, 1, 0, 0, false));
    }

    // exactly what the executor's MarketDataPublisher builds: the platform envelope, bid and ask only
    private static String executorQuote(UUID eventId, String bid, String ask, String eventTime) {
        return """
            {"eventId":"%s","eventType":"QUOTE","eventTime":"%s","source":"executor","schemaVersion":1,
             "payload":{"symbol":"INFY.NS","bid":%s,"ask":%s}}
            """.formatted(eventId, eventTime, bid, ask);
    }

    @Test
    void executorQuote_runsOneCycle_thenCommitsTheOffset() {
        UUID eventId = UUID.randomUUID();

        listener.onQuote(record(executorQuote(eventId, "1499.50", "1500.50", "2026-10-08T09:00:00.1234567Z")), ack);

        ArgumentCaptor<StrategyQuote> quote = ArgumentCaptor.forClass(StrategyQuote.class);
        verify(engine).onQuote(quote.capture());
        assertEquals(eventId, quote.getValue().eventId());
        assertEquals("INFY.NS", quote.getValue().symbol());
        assertEquals(new BigDecimal("1500.0000"), quote.getValue().price());
        assertEquals(new BigDecimal("1499.50"), quote.getValue().bid());
        assertEquals(new BigDecimal("1500.50"), quote.getValue().ask());
        // microseconds, as TIMESTAMPTZ stores it
        assertEquals(OffsetDateTime.parse("2026-10-08T09:00:00.123456Z"), quote.getValue().quotedAt());
        verify(ack).acknowledge();
    }

    @Test
    void cycleFailing_leavesTheOffsetUncommitted_soTheQuoteIsRetried() {
        when(engine.onQuote(any())).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class, () -> listener.onQuote(
            record(executorQuote(UUID.randomUUID(), "1499.50", "1500.50", "2026-10-08T09:00:00Z")), ack));
        verify(ack, never()).acknowledge();
    }

    @Test
    void quoteFlaggedStale_isNeitherStoredNorTradedOn() {
        listener.onQuote(record("""
            {"eventId":"%s","eventType":"QUOTE","eventTime":"2026-10-08T09:00:00Z",
             "payload":{"symbol":"INFY.NS","price":1500,"stale":true}}
            """.formatted(UUID.randomUUID())), ack);

        verifyNoInteractions(engine);
        verify(ack).acknowledge();
    }

    @Test
    void otherEventTypes_areAcknowledgedAndIgnored() {
        listener.onQuote(record("""
            {"eventId":"%s","eventType":"HEARTBEAT","eventTime":"2026-10-08T09:00:00Z","payload":{}}
            """.formatted(UUID.randomUUID())), ack);

        verifyNoInteractions(engine);
        verify(ack).acknowledge();
    }

    @Test
    void quoteWithoutAnEventId_cannotTrade_andGoesToTheDlt() {
        UnusableQuoteException e = assertThrows(UnusableQuoteException.class, () -> listener.onQuote(record("""
            {"eventType":"QUOTE","eventTime":"2026-10-08T09:00:00Z","payload":{"symbol":"INFY.NS","price":1500}}
            """), ack));
        assertEquals("MISSING_EVENT_ID", e.getFailureReason());
        verifyNoInteractions(engine);
    }

    @Test
    void unreadableMessages_areNotRetried() {
        assertEquals("MALFORMED_JSON", assertThrows(UnusableQuoteException.class,
            () -> listener.onQuote(record("{not json"), ack)).getFailureReason());
        assertEquals("MISSING_PRICE", assertThrows(UnusableQuoteException.class, () -> listener.onQuote(record("""
            {"eventId":"%s","eventType":"QUOTE","eventTime":"2026-10-08T09:00:00Z","payload":{"symbol":"INFY.NS"}}
            """.formatted(UUID.randomUUID())), ack)).getFailureReason());
        verify(ack, never()).acknowledge();
    }

    @Test
    void listenerReadsMarketDataOnly_inItsOwnConsumerGroup() throws Exception {
        KafkaListener strategies = StrategyMarketDataListener.class
            .getMethod("onQuote", ConsumerRecord.class, Acknowledgment.class)
            .getAnnotation(KafkaListener.class);

        assertArrayEquals(new String[] {"market-data"}, strategies.topics());
        assertEquals("${strategies.consumer.group-id:strategies-market-data}", strategies.groupId());
        assertEquals("strategyMarketDataContainerFactory", strategies.containerFactory());

        KafkaListener watchlists = MarketDataQuoteListener.class
            .getMethod("onQuote", ConsumerRecord.class, Acknowledgment.class)
            .getAnnotation(KafkaListener.class);
        assertNotEquals(watchlists.groupId(), strategies.groupId());
        assertNotEquals(watchlists.id(), strategies.id());
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("market-data", 0, 0L, "INFY.NS", value);
    }
}
