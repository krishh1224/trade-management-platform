package com.neueda.learning.producer;

import com.neueda.learning.event.KafkaEnvelope;
import com.neueda.learning.event.OrderPlacedPayload;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes order lifecycle events to the orders topic.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String ORDERS_TOPIC = "orders";
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish an ORDER_CANCELLED event for downstream consumers of the orders topic.
     */
    public void publishOrderCancelled(OrderPlacedPayload payload) {
        if (payload == null) {
            log.warn("Cannot publish ORDER_CANCELLED because payload is null");
            return;
        }

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_CANCELLED",
            OffsetDateTime.now(),
            "executor",
            1,
            payload
        );

        try {
            String key = String.valueOf(payload.getAccountId());

            log.info("Publishing ORDER_CANCELLED to topic {} with key {}", ORDERS_TOPIC, key);

            SendResult<String, Object> result = kafkaTemplate.send(ORDERS_TOPIC, key, envelope)
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Published ORDER_CANCELLED to partition {} offset {}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing ORDER_CANCELLED: {}", e.getMessage());
        } catch (TimeoutException | ExecutionException e) {
            log.error("Failed to publish ORDER_CANCELLED: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error publishing ORDER_CANCELLED: {}", e.getMessage());
        }
    }
}
