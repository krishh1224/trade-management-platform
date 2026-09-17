package com.neueda.learning.consumer;

import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.event.KafkaEnvelope;
import com.neueda.learning.event.OrderPlacedPayload;
import com.neueda.learning.service.TradeExecutionService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for ORDER_PLACED events.
 * Deserializes KafkaEnvelope<OrderPlacedPayload>, validates event type, and triggers execution.
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
     * 
     * The message format is: KafkaEnvelope<OrderPlacedPayload> as JSON
     * - eventType: "ORDER_PLACED"
     * - payload: OrderPlacedPayload with orderId (UUID), accountId, symbol, etc.
     */
    @KafkaListener(topics = "orders", groupId = "trade-executor")
    public void consumeOrderPlaced(@Payload KafkaEnvelope<OrderPlacedPayload> envelope) {
        logger.info("Received Kafka envelope: eventType={}, eventId={}, source={}",
            envelope.getEventType(), envelope.getEventId(), envelope.getSource());

        try {
            // Validate event type
            if (!EXPECTED_EVENT_TYPE.equalsIgnoreCase(envelope.getEventType())) {
                logger.debug("Ignoring event with unexpected type: {}", envelope.getEventType());
                return;
            }

            // Extract payload
            OrderPlacedPayload payload = envelope.getPayload();
            if (payload == null) {
                logger.warn("ORDER_PLACED envelope has no payload, ignoring: {}", envelope);
                return;
            }

            // Extract orderId from payload
            UUID orderId = payload.getOrderId();
            if (orderId == null) {
                logger.warn("ORDER_PLACED payload has no orderId, ignoring: {}", payload);
                return;
            }

            logger.info("Processing ORDER_PLACED event for orderId: {}, accountId: {}, symbol: {}",
                orderId, payload.getAccountId(), payload.getSymbol());

            // Convert UUID orderId to Long for execution service
            // Note: ExecutionService may need adjustment if it expects UUID instead of Long
            Long orderIdLong = convertUuidToOrderId(orderId);
            
            // Execute the order
            ExecutionResult result = executionService.executeOrder(orderIdLong);

            logger.info("Order {} execution completed: {}", orderId, result);
            // TODO: In the next story, publish FILLED/REJECTED events and update the database

        } catch (Exception e) {
            logger.error("Error processing ORDER_PLACED event from envelope: {}", envelope, e);
            // Kafka will handle retries based on Spring Kafka configuration
            // For now, we log and continue
        }
    }

    /**
     * Convert UUID to Long for compatibility with ExecutionService.
     * Takes the most significant 64 bits of the UUID.
     */
    private Long convertUuidToOrderId(UUID uuid) {
        return uuid.getMostSignificantBits();
    }
}
