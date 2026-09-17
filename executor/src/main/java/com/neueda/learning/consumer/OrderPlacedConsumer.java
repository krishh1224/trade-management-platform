package com.neueda.learning.consumer;

import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.event.KafkaEnvelope;
import com.neueda.learning.event.OrderPlacedPayload;
import com.neueda.learning.mapper.OrderMapper;
import com.neueda.learning.mapper.PortfolioMapper;
import com.neueda.learning.model.OrderRecord;
import com.neueda.learning.model.PortfolioRecord;
import com.neueda.learning.producer.TradeEventPublisher;
import com.neueda.learning.service.SettlementService;
import com.neueda.learning.service.SettlementService.SettlementResult;
import com.neueda.learning.service.TradeExecutionService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for ORDER_PLACED events from the orders topic.
 * 
 * Flow:
 * 1. Deserialize and validate ORDER_PLACED event
 * 2. Execute the order (get quote, apply decision logic)
 * 3. BEGIN settlement transaction:
 *    - Claim order (first write, guards duplicates)
 *    - Update account balance (with optimistic locking)
 *    - Update portfolio holdings
 *    COMMIT transaction
 * 4. Publish ORDER_FILLED or ORDER_REJECTED event to trade-events topic
 * 5. Acknowledge the consumed message
 *
 * Message acknowledgment is MANUAL and happens only after successful event publishing,
 * ensuring no message loss on error.
 */
@Component
public class OrderPlacedConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedConsumer.class);
    private static final String EXPECTED_EVENT_TYPE = "ORDER_PLACED";

    private final TradeExecutionService executionService;
    private final SettlementService settlementService;
    private final TradeEventPublisher tradeEventPublisher;
    private final OrderMapper orderMapper;
    private final PortfolioMapper portfolioMapper;

    public OrderPlacedConsumer(TradeExecutionService executionService,
                               SettlementService settlementService,
                               TradeEventPublisher tradeEventPublisher,
                               OrderMapper orderMapper,
                               PortfolioMapper portfolioMapper) {
        this.executionService = executionService;
        this.settlementService = settlementService;
        this.tradeEventPublisher = tradeEventPublisher;
        this.orderMapper = orderMapper;
        this.portfolioMapper = portfolioMapper;
    }

    /**
     * Consume ORDER_PLACED event from Kafka.
     * Topic: orders
     * Consumer group: trade-executor
     * Key: accountId (not used in handler, but ensures ordering per account)
     * Acknowledgment: MANUAL (acknowledge only after successful settlement and event publishing)
     * 
     * The message format is: KafkaEnvelope<OrderPlacedPayload> as JSON
     */
    @KafkaListener(topics = "orders", groupId = "trade-executor", properties = "max.poll.records=1")
    public void consumeOrderPlaced(@Payload KafkaEnvelope<OrderPlacedPayload> envelope,
                                   Acknowledgment ack) {
        logger.info("Received ORDER_PLACED: eventId={}", envelope.getEventId());

        try {
            // Validate event type
            if (!EXPECTED_EVENT_TYPE.equalsIgnoreCase(envelope.getEventType())) {
                logger.debug("Ignoring event with unexpected type: {}", envelope.getEventType());
                ack.acknowledge(); // Still acknowledge non-matching events
                return;
            }

            // Extract and validate payload
            OrderPlacedPayload payload = envelope.getPayload();
            if (payload == null) {
                logger.warn("ORDER_PLACED envelope has no payload, ignoring");
                ack.acknowledge();
                return;
            }

            UUID orderId = payload.getOrderId();
            if (orderId == null) {
                logger.warn("ORDER_PLACED payload has no orderId, ignoring");
                ack.acknowledge();
                return;
            }

            logger.info("Processing ORDER_PLACED: orderId={}, accountId={}, symbol={}, side={}, quantity={}",
                orderId, payload.getAccountId(), payload.getSymbol(), payload.getSide(), payload.getQuantity());

            // Convert UUID to Long for database lookup
            Long orderIdLong = convertUuidToOrderId(orderId);

            // 1. Execute the order (get quote, apply decision logic)
            ExecutionResult execution = executionService.executeOrder(orderIdLong);
            logger.info("Execution result: {}", execution);

            // 2. Load order and portfolio from database
            OrderRecord order = orderMapper.findById(orderIdLong);
            if (order == null) {
                logger.warn("Order not found after execution: {}", orderIdLong);
                ack.acknowledge(); // Don't retry - order doesn't exist
                return;
            }

            PortfolioRecord portfolio = portfolioMapper.findById(order.getPortfolioId());
            if (portfolio == null) {
                logger.warn("Portfolio not found for order {}: {}", orderIdLong, order.getPortfolioId());
                ack.acknowledge();
                return;
            }

            Long accountId = portfolio.getAccountId();

            // 3. Settle the order (transactional: claim, update balance, update holdings)
            SettlementResult settlementResult = settlementService.settleOrder(
                execution,
                order,
                accountId,
                order.getPortfolioId(),
                order.getQuantity(),
                order.getInstrumentId()
            );

            logger.info("Settlement result: {}", settlementResult);

            // Handle settlement outcomes
            if (settlementResult.isDuplicate()) {
                // This order was already processed by another delivery (transaction idempotence)
                logger.info("Order {} already processed (duplicate), not publishing event", orderIdLong);
                ack.acknowledge();
                return;
            }

            if (!settlementResult.isSuccess()) {
                // Settlement failed (insufficient balance, holding mismatch, version conflict exhausted, etc.)
                logger.error("Settlement failed for order {}: {}", orderIdLong, settlementResult.getErrorMessage());
                // In production, could publish an ERROR event here for monitoring
                // For now, we don't acknowledge and let Kafka retry
                return; // No ack = retry on next poll
            }

            // 4. Transaction committed (by @Transactional); now publish event
            OffsetDateTime executedOn = execution.isFilled() ? 
                OffsetDateTime.now() : OffsetDateTime.now();

            if (execution.isFilled()) {
                tradeEventPublisher.publishOrderFilled(
                    orderIdLong,
                    accountId,
                    execution.getExecutionPrice(),
                    executedOn,
                    order.getQuantity(),
                    payload.getSymbol(),
                    payload.getSide()
                );
                logger.info("Published ORDER_FILLED for order {}", orderIdLong);
            } else {
                tradeEventPublisher.publishOrderRejected(
                    orderIdLong,
                    accountId,
                    execution.getReason()
                );
                logger.info("Published ORDER_REJECTED for order {}", orderIdLong);
            }

            // 5. Acknowledge the consumed message (only after successful event publishing)
            ack.acknowledge();
            logger.info("Acknowledged ORDER_PLACED message for order {}", orderIdLong);

        } catch (Exception e) {
            logger.error("Unexpected error processing ORDER_PLACED event: {}", e.getMessage(), e);
            // Don't acknowledge; let Kafka retry on next poll
            // In production, could have dead-letter queue logic here
        }
    }

    /**
     * Convert UUID to Long for compatibility with existing ExecutionService.
     * Combines MSB and LSB to create a deterministic Long (handles UUID namespace idempotence).
     */
    private Long convertUuidToOrderId(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return mixed == Long.MIN_VALUE ? 0L : Math.abs(mixed);
    }
}
