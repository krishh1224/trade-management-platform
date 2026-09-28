package com.neueda.learning.producer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class TradeEventPublisherTest {

    private static final OffsetDateTime EXECUTED_ON = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private TradeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TradeEventPublisher(kafkaTemplate);
    }

    @Test
    void publishOrderFilled_sendsEnvelopeToTradeEventsTopicUsingAccountIdKey() {
        when(kafkaTemplate.send(eq("trade-events"), eq("101"), anyString()))
            .thenReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publishOrderFilled(501L, 101L, new BigDecimal("1500.00"), EXECUTED_ON, 10, "INFY.NS", "BUY");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trade-events"), eq("101"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("\"eventType\":\"ORDER_FILLED\""));
        assertTrue(payload.contains("\"orderId\":501"));
        assertTrue(payload.contains("\"executedPrice\":1500.00"));
        assertTrue(payload.contains("\"symbol\":\"INFY.NS\""));
    }

    @Test
    void publishOrderRejected_sendsEnvelopeToTradeEventsTopicUsingAccountIdKey() {
        when(kafkaTemplate.send(eq("trade-events"), eq("102"), anyString()))
            .thenReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publishOrderRejected(505L, 102L, "Quote unavailable after retries");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trade-events"), eq("102"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("\"eventType\":\"ORDER_REJECTED\""));
        assertTrue(payload.contains("\"orderId\":505"));
        assertTrue(payload.contains("\"rejectionReason\":\"Quote unavailable after retries\""));
    }

    @Test
    void publishSwallowsAsyncPublishFailure() {
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Topic trade-events not present in metadata"));
        when(kafkaTemplate.send(eq("trade-events"), eq("101"), anyString())).thenReturn(failedFuture);

        assertDoesNotThrow(() ->
            publisher.publishOrderFilled(501L, 101L, new BigDecimal("1500.00"), EXECUTED_ON, 10, "INFY.NS", "BUY"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishSwallowsInterruptedExceptionAndRestoresInterruptFlag() throws Exception {
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("interrupted"));
        when(kafkaTemplate.send(eq("trade-events"), eq("101"), anyString())).thenReturn(future);

        try {
            assertDoesNotThrow(() ->
                publisher.publishOrderFilled(501L, 101L, new BigDecimal("1500.00"), EXECUTED_ON, 10, "INFY.NS", "BUY"));

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear the flag so it doesn't leak into other tests
        }
    }

    @Test
    void publishSwallowsUnexpectedSynchronousFailure() {
        when(kafkaTemplate.send(eq("trade-events"), eq("101"), anyString()))
            .thenThrow(new RuntimeException("Kafka producer unavailable"));

        assertDoesNotThrow(() ->
            publisher.publishOrderFilled(501L, 101L, new BigDecimal("1500.00"), EXECUTED_ON, 10, "INFY.NS", "BUY"));
    }

    @Test
    void orderFilledPayloadGettersReturnConstructorValues() {
        TradeEventPublisher.OrderFilledPayload payload = new TradeEventPublisher.OrderFilledPayload(
            501L, 101L, new BigDecimal("1500.00"), EXECUTED_ON, 10, "INFY.NS", "BUY");

        assertEquals(501L, payload.getOrderId());
        assertEquals(101L, payload.getAccountId());
        assertEquals(new BigDecimal("1500.00"), payload.getExecutedPrice());
        assertEquals(EXECUTED_ON, payload.getExecutedOn());
        assertEquals(10, payload.getQuantity());
        assertEquals("INFY.NS", payload.getSymbol());
        assertEquals("BUY", payload.getSide());
    }

    @Test
    void orderRejectedPayloadGettersReturnConstructorValues() {
        TradeEventPublisher.OrderRejectedPayload payload =
            new TradeEventPublisher.OrderRejectedPayload(505L, 102L, "Quote unavailable");

        assertEquals(505L, payload.getOrderId());
        assertEquals(102L, payload.getAccountId());
        assertEquals("Quote unavailable", payload.getRejectionReason());
    }

    private static SendResult<String, String> sendResult() {
        ProducerRecord<String, String> record = new ProducerRecord<>("trade-events", "101", "payload");
        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition("trade-events", 1), 0, 5, 0, 0L, 0, 0);
        return new SendResult<>(record, metadata);
    }
}
