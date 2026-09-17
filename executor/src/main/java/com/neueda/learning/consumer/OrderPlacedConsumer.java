package com.neueda.learning.consumer;

import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.event.OrderPlacedEvent;
import com.neueda.learning.service.TradeExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for ORDER_PLACED events.
 * Deserializes event envelope, validates event type, extracts orderId, and triggers execution.
 */
@Component
public class OrderPlacedConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedConsumer.class);
    private static final String EXPECTED_EVENT_TYPE = "ORDER_PLACED";

    private final TradeExecutionService executionService;

    public OrderPlacedConsumer(TradeExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Consume ORDER_PLACED event from Kafka.
     * Topic: orders
     * Consumer group: trade-executor
     * Key: accountId
     */
    @KafkaListener(topics = "orders", groupId = "trade-executor")
    public void consumeOrderPlaced(@Payload OrderPlacedEvent event) {
        logger.info("Received Kafka message: {}", event);

        try {
            // Validate event type
            if (!EXPECTED_EVENT_TYPE.equalsIgnoreCase(event.getEventType())) {
                logger.debug("Ignoring event with unexpected type: {}", event.getEventType());
                return;
            }

            // Extract orderId
            Long orderId = event.getOrderId();
            if (orderId == null) {
                logger.warn("ORDER_PLACED event has no orderId, ignoring: {}", event);
                return;
            }

            logger.info("Processing ORDER_PLACED event for orderId: {}", orderId);

            // Execute the order
            ExecutionResult result = executionService.executeOrder(orderId);

            logger.info("Order {} execution completed: {}", orderId, result);
            // TODO: In the next story, publish FILLED/REJECTED events and update the database

        } catch (Exception e) {
            logger.error("Error processing ORDER_PLACED event: {}", event, e);
            // Kafka will handle retries based on Spring Kafka configuration
            // For now, we log and continue
        }
    }
}
