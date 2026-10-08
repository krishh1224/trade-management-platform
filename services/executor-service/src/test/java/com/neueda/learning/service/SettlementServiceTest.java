package com.neueda.learning.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.domain.enums.ExecutionStatus;
import com.neueda.learning.mapper.AccountMapper;
import com.neueda.learning.mapper.OrderMapper;
import com.neueda.learning.mapper.PortfolioHoldingMapper;
import com.neueda.learning.model.AccountRecord;
import com.neueda.learning.model.OrderRecord;
import com.neueda.learning.model.PortfolioHoldingRecord;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for SettlementService.
 * Tests cover:
 * 1. Successful settlement with atomicity (order, balance, holding updated)
 * 2. Failure rollback (transaction atomicity)
 * 3. Duplicate handling (claimOrderIfNew returns 0)
 * 4. Optimistic lock retry and exhaustion
 * 5. FILLED vs REJECTED settlement paths
 */
class SettlementServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private PortfolioHoldingMapper holdingMapper;

    private SettlementService settlementService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        settlementService = new SettlementService(orderMapper, accountMapper, holdingMapper);
        // Set max retries to a low number for testing using reflection
        Field maxRetriesField = SettlementService.class.getDeclaredField("maxRetries");
        maxRetriesField.setAccessible(true);
        maxRetriesField.setInt(settlementService, 2);
    }

    // ==================== Test 1: Successful FILLED settlement ====================

    @Test
    @DisplayName("Should successfully settle a FILLED BUY order: update order, deduct balance, increase holdings")
    void testSettleFilledBuyOrder() {
        // Given
        long orderId = 1001L;
        long accountId = 101L;
        long portfolioId = 201L;
        long instrumentId = 301L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("150.00");
        BigDecimal tradeValue = new BigDecimal("15000.00"); // 100 * 150

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("50000.00"), 1L);
        PortfolioHoldingRecord holding = null; // First purchase

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "BUY order filled");

        // Mock the order claim (returns 1 = success)
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // Mock account read and update with optimistic lock
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(eq(accountId), eq(new BigDecimal("35000.00")), eq(1L)))
            .thenReturn(1);

        // Mock holding read (doesn't exist for first purchase)
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId))
            .thenReturn(null);

        // Mock holding creation
        when(holdingMapper.insertHolding(anyLong(), eq(portfolioId), eq(instrumentId), eq(100L), eq(executedPrice)))
            .thenReturn(1);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isDuplicate()).isFalse();

        // Verify all three writes happened
        verify(orderMapper).claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull());
        verify(accountMapper).findById(accountId);
        verify(accountMapper).updateBalanceWithVersionCheck(eq(accountId), eq(new BigDecimal("35000.00")), eq(1L));
        verify(holdingMapper).findByPortfolioAndInstrument(portfolioId, instrumentId);
        verify(holdingMapper).insertHolding(anyLong(), eq(portfolioId), eq(instrumentId), eq(100L), eq(executedPrice));
    }

    @Test
    @DisplayName("Should successfully settle a FILLED SELL order: update order, add balance, decrease holdings")
    void testSettleFilledSellOrder() {
        // Given
        long orderId = 1002L;
        long accountId = 102L;
        long portfolioId = 202L;
        long instrumentId = 302L;
        int quantity = 50;
        BigDecimal executedPrice = new BigDecimal("200.00");
        BigDecimal tradeValue = new BigDecimal("10000.00"); // 50 * 200

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "SELL");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("20000.00"), 1L);
        PortfolioHoldingRecord holding = new PortfolioHoldingRecord(
            401L, portfolioId, instrumentId, 100L, new BigDecimal("180.00"), LocalDateTime.now(), LocalDateTime.now()
        );

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "SELL order filled");

        // Mock the order claim (returns 1 = success)
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // Mock account read and update
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(eq(accountId), eq(new BigDecimal("30000.00")), eq(1L)))
            .thenReturn(1);

        // Mock holding read and update
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId))
            .thenReturn(holding);
        when(holdingMapper.updateQuantity(401L, 50L))
            .thenReturn(1);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(accountMapper).updateBalanceWithVersionCheck(eq(accountId), eq(new BigDecimal("30000.00")), eq(1L));
        verify(holdingMapper).updateQuantity(401L, 50L);
    }

    // ==================== Test 2: Successful REJECTED settlement ====================

    @Test
    @DisplayName("Should successfully settle a REJECTED order: update order only, no balance/holding changes")
    void testSettleRejectedOrder() {
        // Given
        long orderId = 1003L;
        long accountId = 103L;
        long portfolioId = 203L;
        long instrumentId = 303L;
        int quantity = 75;

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.REJECTED, null, "Quote unavailable");

        // Mock the order claim (returns 1 = success)
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("REJECTED"), isNull(), any(LocalDateTime.class), eq("Quote unavailable"), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        // Verify order was claimed
        verify(orderMapper).claimOrderIfNew(eq(orderId), eq("REJECTED"), isNull(), any(LocalDateTime.class), eq("Quote unavailable"), isNull(), isNull(), isNull());
        // Verify account and holdings were NOT touched
        verify(accountMapper, never()).findById(any());
        verify(holdingMapper, never()).findByPortfolioAndInstrument(any(), any());
    }

    // ==================== Test 3: Duplicate delivery ====================

    @Test
    @DisplayName("Should handle duplicate delivery: claimOrderIfNew returns 0, skip updates, return duplicate status")
    void testDuplicateDelivery() {
        // Given
        long orderId = 1004L;
        long accountId = 104L;
        long portfolioId = 204L;
        long instrumentId = 304L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("150.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        // Mock the order claim (returns 0 = already processed)
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(0);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isDuplicate()).isTrue();
        // Verify account and holdings were NOT touched
        verify(accountMapper, never()).findById(any());
        verify(holdingMapper, never()).findByPortfolioAndInstrument(any(), any());
    }

    // ==================== Test 4: Optimistic lock retry ====================

    @Test
    @DisplayName("Should retry on version mismatch and eventually succeed")
    void testOptimisticLockRetrySuccess() {
        // Given
        long orderId = 1005L;
        long accountId = 105L;
        long portfolioId = 205L;
        long instrumentId = 305L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("150.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        AccountRecord accountV1 = createAccountRecord(accountId, new BigDecimal("50000.00"), 1L);
        AccountRecord accountV2 = createAccountRecord(accountId, new BigDecimal("45000.00"), 2L); // Updated by another tx

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        // Mock the order claim
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // Mock account: first read returns v1, second read returns v2 (updated by concurrent tx)
        when(accountMapper.findById(accountId))
            .thenReturn(accountV1)  // First attempt
            .thenReturn(accountV2); // After retry

        // Mock account update: first attempt fails (version mismatch), second succeeds
        when(accountMapper.updateBalanceWithVersionCheck(anyLong(), any(BigDecimal.class), anyLong()))
            .thenReturn(0)  // First attempt: version mismatch
            .thenReturn(1); // Retry: success

        // Mock holding creation
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId))
            .thenReturn(null);
        when(holdingMapper.insertHolding(anyLong(), eq(portfolioId), eq(instrumentId), eq(100L), eq(executedPrice)))
            .thenReturn(1);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        // Verify account was read twice (one for each attempt)
        verify(accountMapper, times(2)).findById(accountId);
        // Verify update was attempted twice
        verify(accountMapper, times(2)).updateBalanceWithVersionCheck(anyLong(), any(BigDecimal.class), anyLong());
    }

    @Test
    @DisplayName("Should fail with error when optimistic lock retries exhausted")
    void testOptimisticLockRetryExhausted() {
        // Given
        long orderId = 1006L;
        long accountId = 106L;
        long portfolioId = 206L;
        long instrumentId = 306L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("150.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("50000.00"), 1L);

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        // Mock the order claim
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // Mock account: always returns the same version
        when(accountMapper.findById(accountId))
            .thenReturn(account);

        // Mock account update: always fails (version mismatch)
        when(accountMapper.updateBalanceWithVersionCheck(anyLong(), any(BigDecimal.class), anyLong()))
            .thenReturn(0);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("retries");
        // Verify the update was attempted maxRetries times
        verify(accountMapper, times(2)).updateBalanceWithVersionCheck(anyLong(), any(BigDecimal.class), anyLong());
    }

    // ==================== Test 5: Insufficient balance ====================

    @Test
    @DisplayName("Should fail with insufficient balance for BUY order")
    void testInsufficientBalanceForBuy() {
        // Given
        long orderId = 1007L;
        long accountId = 107L;
        long portfolioId = 207L;
        long instrumentId = 307L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("150.00"); // needs 15000
        BigDecimal insufficientBalance = new BigDecimal("10000.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        AccountRecord account = createAccountRecord(accountId, insufficientBalance, 1L);

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        // Mock the order claim
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // Mock account read
        when(accountMapper.findById(accountId)).thenReturn(account);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Insufficient balance");
        // Verify update was NOT attempted
        verify(accountMapper, never()).updateBalanceWithVersionCheck(anyLong(), any(BigDecimal.class), anyLong());
    }

    @Test
    @DisplayName("Should fail with insufficient holdings for SELL order")
    void testInsufficientHoldingsForSell() {
        // Given
        long orderId = 1008L;
        long accountId = 108L;
        long portfolioId = 208L;
        long instrumentId = 308L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("150.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "SELL");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("50000.00"), 1L);
        PortfolioHoldingRecord holding = new PortfolioHoldingRecord(
            401L, portfolioId, instrumentId, 50L, new BigDecimal("150.00"), LocalDateTime.now(), LocalDateTime.now()
        ); // Only 50, need 100

        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        // Mock the order claim
        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(1);

        // Mock account read and update
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(anyLong(), any(BigDecimal.class), anyLong()))
            .thenReturn(1);

        // Mock holding read (insufficient)
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId))
            .thenReturn(holding);

        // When
        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId
        );

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Insufficient holdings");
        // Verify holding update was NOT attempted
        verify(holdingMapper, never()).updateQuantity(anyLong(), anyLong());
    }

    // ==================== Test 6: Currency conversion ====================

    @Test
    @DisplayName("FILLED USD BUY on an INR account debits the INR settlement amount and records the FX rate")
    void testSettleUsdBuyDebitsInrSettlementAmount() {
        long orderId = 1009L;
        long accountId = 101L;
        long portfolioId = 201L;
        long instrumentId = 307L; // AAPL
        int quantity = 2;
        BigDecimal executedPrice = new BigDecimal("333.73");     // USD
        BigDecimal fxRate = new BigDecimal("96.17096200");
        BigDecimal settlementAmount = new BigDecimal("64190.27"); // INR

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("98000.00"), 3L);
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice,
            "BUY order marketable at ask price", fxRate, "INR", settlementAmount);

        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class),
            isNull(), eq(fxRate), eq("INR"), eq(settlementAmount))).thenReturn(1);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(accountId, new BigDecimal("33809.73"), 3L)).thenReturn(1);
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId)).thenReturn(null);
        when(holdingMapper.insertHolding(anyLong(), eq(portfolioId), eq(instrumentId), eq(2L), eq(executedPrice)))
            .thenReturn(1);

        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId);

        assertThat(result.isSuccess()).isTrue();
        // 98000.00 - 64190.27, NOT 98000.00 - 667.46 (the USD trade value)
        verify(accountMapper).updateBalanceWithVersionCheck(accountId, new BigDecimal("33809.73"), 3L);
        // Average cost stays in the instrument's currency
        verify(holdingMapper).insertHolding(anyLong(), eq(portfolioId), eq(instrumentId), eq(2L), eq(executedPrice));
    }

    @Test
    @DisplayName("FILLED USD SELL on an INR account credits the INR settlement amount")
    void testSettleUsdSellCreditsInrSettlementAmount() {
        long orderId = 1010L;
        long accountId = 101L;
        long portfolioId = 201L;
        long instrumentId = 307L;
        int quantity = 2;
        BigDecimal executedPrice = new BigDecimal("333.65");
        BigDecimal fxRate = new BigDecimal("96.16903800");
        BigDecimal settlementAmount = new BigDecimal("64173.60");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "SELL");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("1000.00"), 1L);
        PortfolioHoldingRecord holding = new PortfolioHoldingRecord(
            410L, portfolioId, instrumentId, 5L, new BigDecimal("320.0000"), LocalDateTime.now(), LocalDateTime.now());
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice,
            "SELL order marketable at bid price", fxRate, "INR", settlementAmount);

        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class),
            isNull(), eq(fxRate), eq("INR"), eq(settlementAmount))).thenReturn(1);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(accountId, new BigDecimal("65173.60"), 1L)).thenReturn(1);
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId)).thenReturn(holding);
        when(holdingMapper.updateQuantity(410L, 3L)).thenReturn(1);

        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId);

        assertThat(result.isSuccess()).isTrue();
        verify(accountMapper).updateBalanceWithVersionCheck(accountId, new BigDecimal("65173.60"), 1L);
        verify(holdingMapper).updateQuantity(410L, 3L); // sell leaves average cost unchanged
    }

    // ==================== Test 7: Holdings average cost and sell-out ====================

    @Test
    @DisplayName("BUY into an existing holding recalculates the weighted average cost")
    void testBuyIntoExistingHoldingRecalculatesAverageCost() {
        long orderId = 1011L;
        long accountId = 102L;
        long portfolioId = 202L;
        long instrumentId = 302L;
        int quantity = 50;
        BigDecimal executedPrice = new BigDecimal("200.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "BUY");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("20000.00"), 1L);
        PortfolioHoldingRecord holding = new PortfolioHoldingRecord(
            402L, portfolioId, instrumentId, 100L, new BigDecimal("180.0000"), LocalDateTime.now(), LocalDateTime.now());
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class),
            isNull(), isNull(), isNull(), isNull())).thenReturn(1);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(accountId, new BigDecimal("10000.00"), 1L)).thenReturn(1);
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId)).thenReturn(holding);
        // (100 x 180 + 50 x 200) / 150 = 28000 / 150 = 186.6667
        when(holdingMapper.updateQuantityAndAvgCost(402L, 150L, new BigDecimal("186.6667"))).thenReturn(1);

        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId);

        assertThat(result.isSuccess()).isTrue();
        verify(holdingMapper).updateQuantityAndAvgCost(402L, 150L, new BigDecimal("186.6667"));
        verify(holdingMapper, never()).updateQuantity(anyLong(), anyLong());
    }

    @Test
    @DisplayName("SELL of the entire position deletes the holding instead of writing quantity 0")
    void testSellEntirePositionDeletesHolding() {
        long orderId = 1012L;
        long accountId = 102L;
        long portfolioId = 202L;
        long instrumentId = 302L;
        int quantity = 100;
        BigDecimal executedPrice = new BigDecimal("200.00");

        OrderRecord order = createOrderRecord(orderId, portfolioId, instrumentId, quantity, "NEW", "SELL");
        AccountRecord account = createAccountRecord(accountId, new BigDecimal("0.00"), 1L);
        PortfolioHoldingRecord holding = new PortfolioHoldingRecord(
            402L, portfolioId, instrumentId, 100L, new BigDecimal("180.0000"), LocalDateTime.now(), LocalDateTime.now());
        ExecutionResult execution = new ExecutionResult(orderId, ExecutionStatus.FILLED, executedPrice, "Filled");

        when(orderMapper.claimOrderIfNew(eq(orderId), eq("FILLED"), eq(executedPrice), any(LocalDateTime.class),
            isNull(), isNull(), isNull(), isNull())).thenReturn(1);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(accountMapper.updateBalanceWithVersionCheck(accountId, new BigDecimal("20000.00"), 1L)).thenReturn(1);
        when(holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId)).thenReturn(holding);
        when(holdingMapper.deleteHolding(402L)).thenReturn(1);

        SettlementService.SettlementResult result = settlementService.settleOrder(
            execution, order, accountId, portfolioId, quantity, instrumentId);

        assertThat(result.isSuccess()).isTrue();
        verify(holdingMapper).deleteHolding(402L);
        verify(holdingMapper, never()).updateQuantity(anyLong(), anyLong());
    }

    // ==================== Helper methods ====================

    private OrderRecord createOrderRecord(long orderId, long portfolioId, long instrumentId,
                                         int quantity, String status, String side) {
        OrderRecord order = new OrderRecord();
        order.setOrderId(orderId);
        order.setPortfolioId(portfolioId);
        order.setInstrumentId(instrumentId);
        order.setQuantity(quantity);
        order.setPrice(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setSide(side);
        order.setIdempotencyKey("idempotency-" + orderId);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private AccountRecord createAccountRecord(long accountId, BigDecimal balance, Long version) {
        AccountRecord account = new AccountRecord();
        account.setAccId(accountId);
        account.setAccountNumber("ACC-" + accountId);
        account.setRiskProfile("MODERATE");
        account.setBaseCurrency("INR");
        account.setBalance(balance);
        account.setStatus("ACTIVE");
        account.setVersion(version);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return account;
    }
}
