package com.team5.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team5.dto.request.PlaceOrderRequest;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

// publishes ORDER_PLACED events to the orders topic after a successful order insert
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String ORDERS_TOPIC = "orders";
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Serialize OffsetDateTime as ISO-8601 strings (e.g., "2026-09-17T08:30:45Z")
        // instead of numeric Unix timestamps. This matches consumer expectations.
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // call this AFTER the transaction has committed, never inside it
    public void publishOrderPlaced(PlaceOrderRequest request, UUID orderId, OffsetDateTime createdOn) {
        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId,
            request.getAccountId(),
            request.getSymbol(),
            request.getSide().name(),
            request.getQuantity(),
            request.getPrice(),
            request.getIdempotencyKey(),
            createdOn
        );

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "trade-api",
            1,
            payload
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);
            String key = String.valueOf(request.getAccountId()); // keyed by accountId

            log.info("Publishing ORDER_PLACED for order {} to topic {} with key {}",
                orderId, ORDERS_TOPIC, key);

            SendResult<String, String> result = kafkaTemplate.send(ORDERS_TOPIC, key, json)
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Published ORDER_PLACED for order {} to partition {} offset {}",
                orderId,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ORDER_PLACED for order {}: {}", orderId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing ORDER_PLACED for order {}: {}",
                orderId, e.getMessage());
        } catch (TimeoutException | ExecutionException e) {
            log.error("Failed to publish ORDER_PLACED for order {}: {}", orderId, e.getMessage());
        } catch (Exception e) {
            // Never let Kafka publishing escape — the order is already committed; a publish
            // failure must not turn into a 500 for the caller.
            log.error("Failed to publish ORDER_PLACED for order {}: {}", orderId, e.getMessage());
        }
    }
}