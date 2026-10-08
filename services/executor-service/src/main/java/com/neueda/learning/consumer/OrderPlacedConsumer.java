package com.neueda.learning.consumer;

import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.event.KafkaEnvelope;
import com.neueda.learning.event.OrderPlacedPayload;
import com.neueda.learning.exception.PermanentFailureException;
import com.neueda.learning.exception.TransientFailureException;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for ORDER_PLACED events from the orders topic.
 *
 * Implements comprehensive failure classification and handling:
 * - PERMANENT failures (malformed JSON, missing orderId, order not found) → DLT immediately, no retry
 * - TRANSIENT failures (database connectivity, settlement conflicts) → retry with bounded backoff, then DLT
 * - Fauxnance outage is NOT classified as permanent (returns REJECTED execution safely)
 *
 * Flow:
 * 1. Deserialize and validate ORDER_PLACED event (PermanentFailureException if validation fails)
 * 2. Execute the order (get quote, apply decision logic)
 * 3. BEGIN settlement transaction:
 *    - Claim order (first write, guards duplicates)
 *    - Update account balance (with optimistic locking)
 *    - Update portfolio holdings
 *    COMMIT transaction
 * 4. Publish ORDER_FILLED or ORDER_REJECTED event to trade-events topic
 * 5. Acknowledge the consumed message
 *
 * ORDER_CANCELLED (published to orders by the trade-api after a successful cancel) is not executed:
 * once the database confirms the order is CANCELLED and belongs to the event's account, it is
 * announced on trade-events, so trade-events keeps a single writer (this service).
 *
 * Message acknowledgment is MANUAL and happens only after successful event publishing,
 * ensuring no message loss on error.
 *
 * Exceptions thrown by this consumer are caught by DefaultErrorHandler configured in KafkaConsumerConfig:
 * - PermanentFailureException: no retry, route to DLT immediately
 * - TransientFailureException: retry with exponential backoff, then route to DLT
 * - Other exceptions: treated as transient (retry with backoff, then DLT)
 */
@Component
public class OrderPlacedConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedConsumer.class);
    private static final String EXPECTED_EVENT_TYPE = "ORDER_PLACED";
    private static final String CANCELLED_EVENT_TYPE = "ORDER_CANCELLED";

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
     *
     * Throws:
     * - PermanentFailureException: for malformed/invalid messages (orderId missing, order not found, etc.)
     * - TransientFailureException: for temporary infrastructure failures (DB connection, settlement conflicts)
     * - Other RuntimeException: for unexpected errors (treated as transient by default)
     *
     * The DefaultErrorHandler configured in KafkaConsumerConfig will classify and route these exceptions:
     * - Permanent → immediate DLT, no retry
     * - Transient → retry with exponential backoff, then DLT
     */
    @KafkaListener(topics = "orders", groupId = "trade-executor", properties = "max.poll.records=1")
    public void consumeOrderPlaced(@Payload KafkaEnvelope<OrderPlacedPayload> envelope,
                                   Acknowledgment ack) {
        if (envelope == null) {
            throw new PermanentFailureException("MALFORMED_JSON", "Envelope is null after deserialization");
        }

        // ORDER_CANCELLED is not executed: confirm it against the database, then announce it on trade-events
        if (CANCELLED_EVENT_TYPE.equalsIgnoreCase(envelope.getEventType())) {
            handleOrderCancelled(envelope, ack);
            return;
        }

        logger.info("Received ORDER_PLACED: eventId={}", envelope.getEventId());

        // Unexpected event types are poison messages for this consumer.
        if (!EXPECTED_EVENT_TYPE.equalsIgnoreCase(envelope.getEventType())) {
            throw new PermanentFailureException(
                "UNEXPECTED_EVENT_TYPE",
                "Expected ORDER_PLACED but got: " + envelope.getEventType()
            );
        }

        // Extract and validate payload
        OrderPlacedPayload payload = envelope.getPayload();
        if (payload == null) {
            throw new PermanentFailureException(
                "MISSING_PAYLOAD",
                "ORDER_PLACED payload is missing for eventId=" + envelope.getEventId()
            );
        }

        UUID orderId = payload.getOrderId();
        if (orderId == null) {
            throw new PermanentFailureException(
                "MISSING_ORDER_ID",
                "ORDER_PLACED payload is missing orderId for eventId=" + envelope.getEventId()
            );
        }

        logger.info("Processing ORDER_PLACED: orderId={}, accountId={}, symbol={}, side={}, quantity={}",
            orderId, payload.getAccountId(), payload.getSymbol(), payload.getSide(), payload.getQuantity());

        try {
            // Convert UUID to Long for database lookup
            Long orderIdLong = convertUuidToOrderId(orderId);

            // Load the current order state before attempting execution.
            OrderRecord order = orderMapper.findById(orderIdLong);
            if (order == null) {
                throw new PermanentFailureException("ORDER_NOT_FOUND",
                    "Order not found in database before execution: " + orderIdLong);
            }

            if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
                // the ORDER_CANCELLED that follows on this partition announces it; nothing to do here
                logger.info("Order {} is CANCELLED, skipping execution", orderIdLong);
                ack.acknowledge();
                return;
            }

            if (!"NEW".equalsIgnoreCase(order.getStatus())) {
                logger.info("Order {} status is {}, skipping execution", orderIdLong, order.getStatus());
                ack.acknowledge();
                return;
            }

            // 1. Execute the order (get quote, apply decision logic)
            ExecutionResult execution = executionService.executeOrder(orderIdLong);
            logger.info("Execution result for order {}: {}", orderIdLong, execution);

            // 2. Load portfolio from database
            PortfolioRecord portfolio = portfolioMapper.findById(order.getPortfolioId());
            if (portfolio == null) {
                // Permanent failure: portfolio not found (data integrity issue)
                throw new PermanentFailureException("PORTFOLIO_NOT_FOUND",
                    "Portfolio not found for order " + orderIdLong + ": " + order.getPortfolioId());
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

            logger.info("Settlement result for order {}: {}", orderIdLong, settlementResult);

            // Handle settlement outcomes
            if (settlementResult.isDuplicate()) {
                // This order was already processed by another delivery (transaction idempotence)
                // This is safe to acknowledge—no retry needed
                logger.info("Order {} already processed (duplicate delivery), not publishing event", orderIdLong);
                ack.acknowledge();
                return;
            }

            if (!settlementResult.isSuccess()) {
                // Settlement failed (insufficient balance, holding mismatch, version conflict, etc.)
                String errorMsg = settlementResult.getErrorMessage();
                logger.error("Settlement failed for order {}: {}", orderIdLong, errorMsg);

                // Classify settlement errors: distinguish between permanent and transient
                if (isTransientSettlementError(errorMsg)) {
                    // Transient error (e.g., optimistic lock retries exhausted)
                    // Signal for retry with bounded backoff
                    throw new TransientFailureException("SETTLEMENT_CONFLICT",
                        "Settlement failed due to transient conflict: " + errorMsg,
                        true); // retriesExhausted = true (already retried internally)
                } else {
                    // Permanent error (e.g., insufficient balance, insufficient holdings)
                    // This is a business rule violation, not a transient infrastructure issue
                    throw new PermanentFailureException("SETTLEMENT_FAILED",
                        "Settlement failed (permanent): " + errorMsg);
                }
            }

            // 4. Transaction committed (by @Transactional); now publish event
            OffsetDateTime executedOn = OffsetDateTime.now();

            if (execution.isFilled()) {
                tradeEventPublisher.publishOrderFilled(
                    orderIdLong,
                    accountId,
                    execution.getExecutionPrice(),
                    executedOn,
                    order.getQuantity(),
                    payload.getSymbol(),
                    payload.getSide(),
                    execution.getFxRate(),
                    execution.getSettlementCurrency(),
                    execution.getSettlementAmount()
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
            logger.info("Successfully processed and acknowledged ORDER_PLACED message for order {}", orderIdLong);

        } catch (PermanentFailureException | TransientFailureException e) {
            // These exceptions are explicitly thrown and will be caught by DefaultErrorHandler
            // for proper classification and routing to DLT
            logger.error("Known failure type: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            // Database connectivity issues are transient
            logger.error("Database access error processing order: {}", e.getMessage(), e);
            throw new TransientFailureException("DATABASE_ERROR",
                "Database access error: " + e.getMessage(), e);
        } catch (Exception e) {
            // Unexpected errors are logged and re-thrown as transient (will retry then DLT)
            logger.error("Unexpected error processing ORDER_PLACED event: {}", e.getMessage(), e);
            throw new TransientFailureException("UNEXPECTED_ERROR",
                "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Determine if a settlement error is transient or permanent.
     * Transient errors (should retry): optimistic lock conflicts, concurrent modifications
     * Permanent errors (no retry): insufficient balance, insufficient holdings, data integrity
     *
     * @param errorMessage the settlement error message
     * @return true if the error is transient and should be retried
     */
    private boolean isTransientSettlementError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        // Transient patterns: optimistic lock conflicts, concurrent modifications
        return errorMessage.contains("version mismatch")
            || errorMessage.contains("concurrent modification")
            || errorMessage.contains("retries");
    }

    /**
     * ORDER_CANCELLED from the trade-api: publish it to trade-events only when the database agrees.
     *
     * The message is a trigger, not the truth. The order must exist, belong to the event's account
     * and be CANCELLED in the database; a cancel the database does not show (the order was filled
     * first, or the message is foreign) is acknowledged without announcing anything.
     */
    private void handleOrderCancelled(KafkaEnvelope<OrderPlacedPayload> envelope, Acknowledgment ack) {
        OrderPlacedPayload payload = envelope.getPayload();
        if (payload == null || payload.getOrderId() == null) {
            throw new PermanentFailureException(
                "MISSING_ORDER_ID",
                "ORDER_CANCELLED is missing its orderId for eventId=" + envelope.getEventId()
            );
        }

        Long orderIdLong = convertUuidToOrderId(payload.getOrderId());
        logger.info("Received ORDER_CANCELLED: eventId={}, orderId={}", envelope.getEventId(), orderIdLong);

        OrderRecord order;
        PortfolioRecord portfolio;
        try {
            order = orderMapper.findById(orderIdLong);
            portfolio = order == null ? null : portfolioMapper.findById(order.getPortfolioId());
        } catch (DataAccessException e) {
            throw new TransientFailureException("DB_UNAVAILABLE",
                "Could not load order " + orderIdLong + " for ORDER_CANCELLED: " + e.getMessage());
        }

        if (order == null) {
            throw new PermanentFailureException("ORDER_NOT_FOUND",
                "ORDER_CANCELLED for an order that does not exist: " + orderIdLong);
        }
        if (portfolio == null || portfolio.getAccountId() == null
            || portfolio.getAccountId() != payload.getAccountId()) {
            throw new PermanentFailureException("ACCOUNT_MISMATCH",
                "ORDER_CANCELLED for order " + orderIdLong + " does not match the order's account");
        }
        if (!"CANCELLED".equalsIgnoreCase(order.getStatus())) {
            logger.warn("ORDER_CANCELLED for order {} but the database says {}; not announcing a cancel",
                orderIdLong, order.getStatus());
            ack.acknowledge();
            return;
        }

        // side, quantity and price come from the database; the symbol is the trade-api's ticker
        tradeEventPublisher.publishOrderCancelled(
            orderIdLong,
            portfolio.getAccountId(),
            payload.getSymbol(),
            order.getSide(),
            order.getQuantity(),
            order.getPrice(),
            OffsetDateTime.now()
        );
        ack.acknowledge();
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

