package com.neueda.learning.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.learning.event.KafkaEnvelope;
import java.math.BigDecimal;
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
 * Publishes ORDER_FILLED and ORDER_REJECTED events to the trade-events topic
 * after a successful settlement transaction.
 *
 * This component should only be called AFTER the DB transaction has committed,
 * never inside the transaction.
 */
@Component
public class TradeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeEventPublisher.class);
    private static final String TRADE_EVENTS_TOPIC = "trade-events";
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TradeEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Serialize OffsetDateTime as ISO-8601 strings instead of numeric timestamps
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Publish an ORDER_FILLED event.
     * Call AFTER the DB transaction (order, account, holding updates) has committed.
     *
     * @param orderId the order ID
     * @param accountId the account ID (used as message key)
     * @param executedPrice the execution price
     * @param executedOn the execution timestamp
     * @param quantity the filled quantity
     * @param symbol the instrument symbol
     * @param side the order side (BUY/SELL)
     */
    public void publishOrderFilled(Long orderId, Long accountId, BigDecimal executedPrice,
                                   OffsetDateTime executedOn, Integer quantity, String symbol, String side) {
        publishOrderFilled(orderId, accountId, executedPrice, executedOn, quantity, symbol, side, null, null, null);
    }

    /**
     * ORDER_FILLED including the settlement figures: the FX rate applied, the account's base
     * currency and the cash amount moved in that currency. The three fields are additive; consumers
     * that predate them ignore unknown fields.
     */
    public void publishOrderFilled(Long orderId, Long accountId, BigDecimal executedPrice,
                                   OffsetDateTime executedOn, Integer quantity, String symbol, String side,
                                   BigDecimal fxRate, String settlementCurrency, BigDecimal settlementAmount) {
        OrderFilledPayload payload = new OrderFilledPayload(
            orderId,
            accountId,
            executedPrice,
            executedOn,
            quantity,
            symbol,
            side,
            fxRate,
            settlementCurrency,
            settlementAmount
        );

        publishEvent("ORDER_FILLED", accountId, payload);
    }

    /**
     * Publish an ORDER_REJECTED event.
     * Call AFTER the DB transaction (order update) has committed.
     *
     * @param orderId the order ID
     * @param accountId the account ID (used as message key)
     * @param rejectionReason the reason for rejection
     */
    public void publishOrderRejected(Long orderId, Long accountId, String rejectionReason) {
        OrderRejectedPayload payload = new OrderRejectedPayload(
            orderId,
            accountId,
            rejectionReason
        );

        publishEvent("ORDER_REJECTED", accountId, payload);
    }

    /**
     * Generic event publisher for trade events.
     */
    private void publishEvent(String eventType, Long accountId, Object payload) {
        KafkaEnvelope<?> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            eventType,
            OffsetDateTime.now(),
            "executor",
            1,
            payload
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);
            String key = String.valueOf(accountId); // keyed by accountId for ordering guarantees

            log.info("Publishing {} to topic {} with key {}", eventType, TRADE_EVENTS_TOPIC, key);

            SendResult<String, String> result = kafkaTemplate.send(TRADE_EVENTS_TOPIC, key, json)
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Published {} to partition {} offset {}",
                eventType,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {}: {}", eventType, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing {}: {}", eventType, e.getMessage());
        } catch (TimeoutException | ExecutionException e) {
            log.error("Failed to publish {}: {}", eventType, e.getMessage());
        } catch (Exception e) {
            // Never let Kafka publishing escape — the order is already committed; a publish
            // failure must not cascade into a critical error for the executor.
            log.error("Unexpected error publishing {}: {}", eventType, e.getMessage());
        }
    }

    /**
     * Payload for ORDER_FILLED event.
     */
    public static class OrderFilledPayload {
        public final Long orderId;
        public final Long accountId;
        public final BigDecimal executedPrice;
        public final OffsetDateTime executedOn;
        public final Integer quantity;
        public final String symbol;
        public final String side;
        public final BigDecimal fxRate;
        public final String settlementCurrency;
        public final BigDecimal settlementAmount;

        public OrderFilledPayload(Long orderId, Long accountId, BigDecimal executedPrice,
                                  OffsetDateTime executedOn, Integer quantity, String symbol, String side) {
            this(orderId, accountId, executedPrice, executedOn, quantity, symbol, side, null, null, null);
        }

        public OrderFilledPayload(Long orderId, Long accountId, BigDecimal executedPrice,
                                  OffsetDateTime executedOn, Integer quantity, String symbol, String side,
                                  BigDecimal fxRate, String settlementCurrency, BigDecimal settlementAmount) {
            this.orderId = orderId;
            this.accountId = accountId;
            this.executedPrice = executedPrice;
            this.executedOn = executedOn;
            this.quantity = quantity;
            this.symbol = symbol;
            this.side = side;
            this.fxRate = fxRate;
            this.settlementCurrency = settlementCurrency;
            this.settlementAmount = settlementAmount;
        }

        public Long getOrderId() {
            return orderId;
        }

        public Long getAccountId() {
            return accountId;
        }

        public BigDecimal getExecutedPrice() {
            return executedPrice;
        }

        public OffsetDateTime getExecutedOn() {
            return executedOn;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getSide() {
            return side;
        }

        public BigDecimal getFxRate() {
            return fxRate;
        }

        public String getSettlementCurrency() {
            return settlementCurrency;
        }

        public BigDecimal getSettlementAmount() {
            return settlementAmount;
        }
    }

    /**
     * Payload for ORDER_REJECTED event.
     */
    public static class OrderRejectedPayload {
        public final Long orderId;
        public final Long accountId;
        public final String rejectionReason;

        public OrderRejectedPayload(Long orderId, Long accountId, String rejectionReason) {
            this.orderId = orderId;
            this.accountId = accountId;
            this.rejectionReason = rejectionReason;
        }

        public Long getOrderId() {
            return orderId;
        }

        public Long getAccountId() {
            return accountId;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }
}
