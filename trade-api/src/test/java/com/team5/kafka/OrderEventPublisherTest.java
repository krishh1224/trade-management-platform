package com.team5.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.OrderSide;
import com.team5.dto.request.PlaceOrderRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.UUID;
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
class OrderEventPublisherTest {

    private static final UUID ORDER_ID = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
    private static final OffsetDateTime CREATED_ON = OffsetDateTime.parse("2026-09-16T17:20:00Z");

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate);
    }

    @Test
    void publishOrderPlaced_sendsEnvelopeToOrdersTopicUsingAccountIdKey() {
        when(kafkaTemplate.send(eq("orders"), eq("101"), anyString()))
            .thenReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publishOrderPlaced(request(), ORDER_ID, CREATED_ON);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("orders"), eq("101"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("\"eventType\":\"ORDER_PLACED\""));
        assertTrue(payload.contains("\"orderId\":\"" + ORDER_ID + "\""));
        assertTrue(payload.contains("\"symbol\":\"INFY.NS\""));
        assertTrue(payload.contains("\"idempotencyKey\":\"test-order-567\""));
    }

    @Test
    void publishOrderPlaced_swallowsAsyncPublishFailure() {
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException("Topic orders not present in metadata after 5000 ms"));
        when(kafkaTemplate.send(eq("orders"), eq("101"), anyString())).thenReturn(failedFuture);

        assertDoesNotThrow(() -> publisher.publishOrderPlaced(request(), ORDER_ID, CREATED_ON));
    }

    private static PlaceOrderRequest request() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(101L);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("233.00"));
        request.setIdempotencyKey("test-order-567");
        return request;
    }

    private static SendResult<String, String> sendResult() {
        ProducerRecord<String, String> record = new ProducerRecord<>("orders", "101", "payload");
        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition("orders", 2),
            0,
            14,
            0,
            0L,
            0,
            0
        );
        return new SendResult<>(record, metadata);
    }
}