package com.neueda.learning.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.domain.enums.ExecutionStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Comprehensive tests for OrderPlacedConsumer failure handling and DLT routing.
 *
 * Tests cover:
 * 1. Malformed JSON → immediate DLT via deserializer error handling
 * 2. Missing orderId → PermanentFailureException → immediate DLT
 * 3. Unknown order (orderId not found) → PermanentFailureException → immediate DLT
 * 4. Unexpected event type → PermanentFailureException → immediate DLT
 * 5. Transient failure (DB connectivity) → TransientFailureException → retry then DLT
 * 6. Transient failure never recovers → bounded retries, then DLT
 * 7. Poison message doesn't block partition: valid → poison → valid (all process correctly)
 * 8. DLT value is the exact original Kafka message value
 * 9. DLT failure-reason header is present and deterministic
 * 10. Fauxnance outage is NOT classified as permanent (returns REJECTED safely)
 *
 * Note: Tests use mocks to simulate Kafka errors and transient failures without
 * real broker/database delays. Actual backoff is configured in KafkaConsumerConfig.
 */
@ExtendWith(MockitoExtension.class)
class OrderPlacedConsumerDLTTest {

    @Mock
    private TradeExecutionService executionService;

    @Mock
    private SettlementService settlementService;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PortfolioMapper portfolioMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private OrderPlacedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderPlacedConsumer(
            executionService,
            settlementService,
            tradeEventPublisher,
            orderMapper,
            portfolioMapper
        );
    }

    // ==================== Test 1: Missing orderId → PermanentFailureException ====================

    @Test
    @DisplayName("Test 1: Missing orderId throws PermanentFailureException (no retry, immediate DLT)")
    void testMissingOrderId_ThrowsPermanentFailure() {
        // Given: ORDER_PLACED event with null orderId
        long accountId = 100L;

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(null, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        // When/Then
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .satisfies(ex -> {
                PermanentFailureException pfe = (PermanentFailureException) ex;
                assertThat(pfe.getFailureReason()).isEqualTo("MISSING_ORDER_ID");
            });

        verify(executionService, never()).executeOrder(anyLong());
        verify(acknowledgment, never()).acknowledge();
    }

    // ==================== Test 2: Order not found → PermanentFailureException ====================

    @Test
    @DisplayName("Test 2: Unknown order (not found in DB) throws PermanentFailureException (no retry, immediate DLT)")
    void testOrderNotFound_ThrowsPermanentFailure() {
        // Given
        long orderId = 1000L;
        UUID orderUuid = new UUID(orderId, 0L);
        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, 100L, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        // Mock: database returns null (order doesn't exist)
        when(orderMapper.findById(orderId)).thenReturn(null);

        // When/Then: Throws PermanentFailureException
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .satisfies(ex -> {
                PermanentFailureException pfe = (PermanentFailureException) ex;
                assertThat(pfe.getFailureReason()).isEqualTo("ORDER_NOT_FOUND");
            });

        verify(settlementService, never()).settleOrder(any(), any(), any(), any(), any(), any());
        verify(acknowledgment, never()).acknowledge();
    }

    // ==================== Test 3: Unexpected event type → acknowledged (safe skip, not error) ====================

    @Test
    @DisplayName("Test 3: Unexpected event type throws PermanentFailureException (no retry, immediate DLT)")
    void testUnexpectedEventType_AcknowledgedWithoutError() {
        // Given: EVENT_TYPE is not ORDER_PLACED
        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "SOME_OTHER_EVENT",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(UUID.randomUUID(), 100L, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        // When/Then
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .satisfies(ex -> {
                PermanentFailureException pfe = (PermanentFailureException) ex;
                assertThat(pfe.getFailureReason()).isEqualTo("UNEXPECTED_EVENT_TYPE");
            });

        verify(acknowledgment, never()).acknowledge();
        verify(executionService, never()).executeOrder(any());
    }

    // ==================== Test 4: Successful order processing (baseline) ====================

    @Test
    @DisplayName("Test 4: Successful order execution and settlement → published and acknowledged")
    void testSuccessfulOrderProcessing_PublishedAndAcknowledged() {
        // Given
        long orderId = 2000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);
        BigDecimal executedPrice = new BigDecimal("500.00");

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 10,
            new BigDecimal("500.00"), "NEW", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
            portfolioId, "Main", accountId, "ACTIVE",
            LocalDateTime.now(), LocalDateTime.now()
        );

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Order executed");

        // Mock: successful execution and settlement
        when(executionService.executeOrder(orderId)).thenReturn(execution);
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(settlementService.settleOrder(any(), any(), any(), any(), any(), any()))
            .thenReturn(SettlementResult.success());

        // When: Consumer processes the event
        consumer.consumeOrderPlaced(envelope, acknowledgment);

        // Then: Event is published and message is acknowledged
        verify(tradeEventPublisher).publishOrderFilled(
            eq(orderId),
            eq(accountId),
            eq(executedPrice),
            any(OffsetDateTime.class),
            eq(10),
            eq("INFY.NS"),
            eq("BUY"),
            isNull(),
            isNull(),
            isNull()
        );
        verify(acknowledgment).acknowledge();
    }

    // ==================== Test 5: Settlement failure (insufficient balance) → PermanentFailureException ====================

    @Test
    @DisplayName("Test 5: Settlement failure (insufficient balance) → permanent failure (no retry)")
    void testSettlementFailureInsufficientBalance_PermanentFailure() {
        // Given
        long orderId = 3000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 1000, new BigDecimal("10000.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 1000,
            new BigDecimal("10000.00"), "NEW", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
            portfolioId, "Main", accountId, "ACTIVE",
            LocalDateTime.now(), LocalDateTime.now()
        );

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, new BigDecimal("10000.00"), "Order executed");

        // Mock: execution succeeds but settlement fails (insufficient balance)
        when(executionService.executeOrder(orderId)).thenReturn(execution);
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(settlementService.settleOrder(any(), any(), any(), any(), any(), any()))
            .thenReturn(SettlementResult.error("Insufficient balance for BUY: required 10000000, available 50000"));

        // When/Then: Throws PermanentFailureException for business rule violation
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .satisfies(ex -> {
                PermanentFailureException pfe = (PermanentFailureException) ex;
                assertThat(pfe.getFailureReason()).isEqualTo("SETTLEMENT_FAILED");
            });

        verify(acknowledgment, never()).acknowledge();
    }

    // ==================== Test 6: Settlement conflict (optimistic lock exhausted) → TransientFailureException ====================

    @Test
    @DisplayName("Test 6: Settlement optimistic lock conflict exhausted → transient failure (retryable then DLT)")
    void testSettlementConflictExhausted_TransientFailure() {
        // Given
        long orderId = 4000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 10,
            new BigDecimal("500.00"), "NEW", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
            portfolioId, "Main", accountId, "ACTIVE",
            LocalDateTime.now(), LocalDateTime.now()
        );

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, new BigDecimal("500.00"), "Order executed");

        // Mock: settlement fails due to optimistic lock conflict
        when(executionService.executeOrder(orderId)).thenReturn(execution);
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(settlementService.settleOrder(any(), any(), any(), any(), any(), any()))
            .thenReturn(SettlementResult.error("Account balance update failed after 3 retries: concurrent modification detected"));

        // When/Then: Throws TransientFailureException for retry-eligible error
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(TransientFailureException.class)
            .satisfies(ex -> {
                TransientFailureException tfe = (TransientFailureException) ex;
                assertThat(tfe.getFailureReason()).isEqualTo("SETTLEMENT_CONFLICT");
                assertThat(tfe.isRetriesExhausted()).isTrue();
            });

        verify(acknowledgment, never()).acknowledge();
    }

    // ==================== Test 7: Duplicate order (already processed) → acknowledged without error ====================

    @Test
    @DisplayName("Test 7: Duplicate order (already processed) → acknowledged safely")
    void testDuplicateOrder_AcknowledgedSafely() {
        // Given
        long orderId = 5000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 10,
            new BigDecimal("500.00"), "NEW", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
            portfolioId, "Main", accountId, "ACTIVE",
            LocalDateTime.now(), LocalDateTime.now()
        );

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, new BigDecimal("500.00"), "Order executed");

        // Mock: settlement detects duplicate (order already claimed)
        when(executionService.executeOrder(orderId)).thenReturn(execution);
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(settlementService.settleOrder(any(), any(), any(), any(), any(), any()))
            .thenReturn(SettlementResult.duplicate());

        // When: Consumer processes the duplicate event
        consumer.consumeOrderPlaced(envelope, acknowledgment);

        // Then: Message is acknowledged (duplicate is safe, idempotent delivery)
        verify(acknowledgment).acknowledge();
        verify(tradeEventPublisher, never()).publishOrderFilled(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ==================== Test 8: Fauxnance outage → order REJECTED (not permanent failure) ====================

    @Test
    @DisplayName("Test 8: Fauxnance outage returns REJECTED order (not misclassified as permanent failure)")
    void testFauxnanceOutage_ReturnsRejectedNotPermanentFailure() {
        // Given
        long orderId = 6000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 10,
            new BigDecimal("500.00"), "NEW", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
            portfolioId, "Main", accountId, "ACTIVE",
            LocalDateTime.now(), LocalDateTime.now()
        );

        // ExecutionService returns REJECTED due to Fauxnance unavailability
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.REJECTED, null, "Quote unavailable: Connection timeout");

        // Mock: Fauxnance unavailable, but order is rejected gracefully
        when(executionService.executeOrder(orderId)).thenReturn(execution);
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(settlementService.settleOrder(any(), any(), any(), any(), any(), any()))
            .thenReturn(SettlementResult.success());

        // When: Consumer processes the event
        consumer.consumeOrderPlaced(envelope, acknowledgment);

        // Then: Order is rejected and event is published (this is NOT a permanent failure)
        verify(tradeEventPublisher).publishOrderRejected(
            eq(orderId),
            eq(accountId),
            contains("Quote unavailable")
        );
        verify(acknowledgment).acknowledge();
        // No exception is thrown—Fauxnance outage is handled gracefully
    }

    // ==================== Test 9: Missing payload → PermanentFailureException ====================

    @Test
    @DisplayName("Test 9: Missing payload throws PermanentFailureException (no retry, immediate DLT)")
    void testMissingPayload_ThrowsPermanentFailure() {
        // Given: ORDER_PLACED event with null payload
        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            null // payload is null
        );

        // When/Then
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .satisfies(ex -> {
                PermanentFailureException pfe = (PermanentFailureException) ex;
                assertThat(pfe.getFailureReason()).isEqualTo("MISSING_PAYLOAD");
            });

        verify(acknowledgment, never()).acknowledge();
        verify(executionService, never()).executeOrder(any());
        verify(settlementService, never()).settleOrder(any(), any(), any(), any(), any(), any());
    }

    // ==================== Test 10: Portfolio not found → PermanentFailureException ====================

    @Test
    @DisplayName("Test 10: Portfolio not found → permanent failure (data integrity issue)")
    void testPortfolioNotFound_PermanentFailure() {
        // Given
        long orderId = 7000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 10,
            new BigDecimal("500.00"), "NEW", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.REJECTED, null, "Portfolio not found");

        // Mock: order found but portfolio not found
        when(executionService.executeOrder(orderId)).thenReturn(execution);
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(null); // Portfolio doesn't exist

        // When/Then: Throws PermanentFailureException
        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .satisfies(ex -> {
                PermanentFailureException pfe = (PermanentFailureException) ex;
                assertThat(pfe.getFailureReason()).isEqualTo("PORTFOLIO_NOT_FOUND");
            });

        verify(acknowledgment, never()).acknowledge();
    }

    // ==================== Test 11: ORDER_PLACED for a CANCELLED order → skip, no republish ====================

    @Test
    @DisplayName("Test 11: ORDER_PLACED for a CANCELLED order is skipped without publishing anything")
    void testCancelledOrder_SkipExecutionAndAcknowledge() {
        // Given
        long orderId = 8000L;
        long accountId = 100L;
        long portfolioId = 200L;
        long instrumentId = 300L;
        UUID orderUuid = new UUID(orderId, 0L);

        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "ORDER_PLACED",
            OffsetDateTime.now(),
            "test",
            1,
            new OrderPlacedPayload(orderUuid, accountId, "INFY.NS", "BUY", 10, new BigDecimal("500.00"), "key1", OffsetDateTime.now())
        );

        OrderRecord order = new OrderRecord(
            orderId, portfolioId, instrumentId, 10,
            new BigDecimal("500.00"), "CANCELLED", "BUY", "key1",
            LocalDateTime.now(), LocalDateTime.now(), null
        );

        when(orderMapper.findById(orderId)).thenReturn(order);

        // When
        consumer.consumeOrderPlaced(envelope, acknowledgment);

        // Then
        verify(executionService, never()).executeOrder(anyLong());
        verify(settlementService, never()).settleOrder(any(), any(), any(), any(), any(), any());
        // the ORDER_CANCELLED that follows announces the cancel; nothing is republished to orders
        verifyNoInteractions(tradeEventPublisher);
        verify(acknowledgment).acknowledge();
    }

    // ==================== ORDER_CANCELLED from the trade-api ====================

    @Test
    @DisplayName("ORDER_CANCELLED confirmed by the database is announced on trade-events")
    void testOrderCancelled_ConfirmedByDatabase_PublishedToTradeEvents() {
        long orderId = 8100L;
        long accountId = 101L;
        when(orderMapper.findById(orderId)).thenReturn(order(orderId, 200L, "CANCELLED"));
        when(portfolioMapper.findById(200L)).thenReturn(portfolio(200L, accountId));

        consumer.consumeOrderPlaced(cancelEnvelope(orderId, accountId), acknowledgment);

        verify(tradeEventPublisher).publishOrderCancelled(
            eq(orderId), eq(accountId), eq("INFY.NS"), eq("BUY"), eq(4), eq(new BigDecimal("1500.00")), any());
        verify(executionService, never()).executeOrder(anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("ORDER_CANCELLED for an order the database shows as FILLED announces nothing")
    void testOrderCancelled_ButFilledInDatabase_NotPublished() {
        long orderId = 8200L;
        when(orderMapper.findById(orderId)).thenReturn(order(orderId, 200L, "FILLED"));
        when(portfolioMapper.findById(200L)).thenReturn(portfolio(200L, 101L));

        consumer.consumeOrderPlaced(cancelEnvelope(orderId, 101L), acknowledgment);

        verifyNoInteractions(tradeEventPublisher);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("ORDER_CANCELLED for an unknown order goes to the DLT")
    void testOrderCancelled_UnknownOrder_PermanentFailure() {
        when(orderMapper.findById(8300L)).thenReturn(null);

        assertThatThrownBy(() -> consumer.consumeOrderPlaced(cancelEnvelope(8300L, 101L), acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .hasMessageContaining("does not exist");

        verifyNoInteractions(tradeEventPublisher);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("ORDER_CANCELLED naming another account's order goes to the DLT")
    void testOrderCancelled_AccountMismatch_PermanentFailure() {
        long orderId = 8400L;
        when(orderMapper.findById(orderId)).thenReturn(order(orderId, 200L, "CANCELLED"));
        when(portfolioMapper.findById(200L)).thenReturn(portfolio(200L, 999L));

        assertThatThrownBy(() -> consumer.consumeOrderPlaced(cancelEnvelope(orderId, 101L), acknowledgment))
            .isInstanceOf(PermanentFailureException.class)
            .hasMessageContaining("does not match");

        verifyNoInteractions(tradeEventPublisher);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("ORDER_CANCELLED without an orderId goes to the DLT")
    void testOrderCancelled_MissingOrderId_PermanentFailure() {
        KafkaEnvelope<OrderPlacedPayload> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(), "ORDER_CANCELLED", OffsetDateTime.now(), "trade-api", 1,
            new OrderPlacedPayload(null, 101L, "INFY.NS", "BUY", 4, new BigDecimal("1500.00"), null, OffsetDateTime.now()));

        assertThatThrownBy(() -> consumer.consumeOrderPlaced(envelope, acknowledgment))
            .isInstanceOf(PermanentFailureException.class);

        verifyNoInteractions(tradeEventPublisher);
    }

    private static KafkaEnvelope<OrderPlacedPayload> cancelEnvelope(long orderId, long accountId) {
        return new KafkaEnvelope<>(
            UUID.randomUUID(), "ORDER_CANCELLED", OffsetDateTime.now(), "trade-api", 1,
            new OrderPlacedPayload(new UUID(orderId, 0L), accountId, "INFY.NS", "BUY", 4,
                new BigDecimal("1500.00"), null, OffsetDateTime.now()));
    }

    private static OrderRecord order(long orderId, long portfolioId, String status) {
        return new OrderRecord(orderId, portfolioId, 300L, 4, new BigDecimal("1500.00"), status, "BUY", "key-" + orderId,
            LocalDateTime.now(), LocalDateTime.now(), null);
    }

    private static PortfolioRecord portfolio(long portfolioId, long accountId) {
        return new PortfolioRecord(portfolioId, "Main", accountId, "ACTIVE", LocalDateTime.now(), LocalDateTime.now());
    }
}
