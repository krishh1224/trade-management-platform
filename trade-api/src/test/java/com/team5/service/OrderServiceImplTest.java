package com.team5.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.OrderConflictException;
import com.team5.domain.exception.OrderNotFoundException;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.OrderMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final UUID ORDER_UUID = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    @Mock
    private AccountQueryMapper accountQueryMapper;

    @Mock
    private OrderMapper orderMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
            accountQueryMapper, orderMapper, new AccountAccessAuthorizer());
    }

    // Removed: concurrentUpdateIsDetectedThroughVersionMismatch
    // This tested the account-balance optimistic lock, which lived in the
    // cash-update logic removed from placeOrder() in Story 3 (Sprint 7).
    // The Trade REST API no longer touches account balance — the executor
    // does that after a real fill. This scenario has no equivalent here
    // anymore; it will be re-covered by the executor's own tests in Story 5.

    @Test
    void placeOrderInsertsOrderAtNewStatusAndReturnsNewResponse() {
        Map<String, Object> account = new HashMap<>();
        account.put("status", "ACTIVE");

        Map<String, Object> instrument = new HashMap<>();
        instrument.put("instrument_id", 301L);
        instrument.put("tradable", true);

        OrderResponse expected = new OrderResponse(
            "ORD-" + ORDER_UUID, OrderStatus.NEW, "Order accepted", "INFY.NS", OrderSide.BUY, 10, new BigDecimal("1000.00"));

        BigDecimal storedPrice = new BigDecimal("1000.00").setScale(4, RoundingMode.HALF_UP);

        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);
        when(orderMapper.findInstrumentByTicker("INFY.NS")).thenReturn(instrument);
        when(orderMapper.findPortfolioIdByAccountId(101L)).thenReturn(201L);
        when(orderMapper.insertOrder(
            anyLong(), eq(201L), eq(301L), eq("BUY"), eq(10L), eq(storedPrice),
            eq("NEW"), eq("idem-version-mismatch"), any(), any()))
            .thenReturn(1);
        when(orderMapper.findOrderById(anyLong(), anyString())).thenReturn(expected);

        OrderResponse response = orderService.placeOrder(101L, orderRequest(101L));

        assertSame(expected, response);
        assertEquals(OrderStatus.NEW, response.status());
        verify(orderMapper).insertOrder(
            anyLong(), eq(201L), eq(301L), eq("BUY"), eq(10L), eq(storedPrice),
            eq("NEW"), eq("idem-version-mismatch"), any(), any());
    }

    @Test
    void placeOrderForAnotherAccountIsRejectedBeforeAnyDatabaseAccess() {
        assertThrows(AccountAccessDeniedException.class, () -> orderService.placeOrder(999L, orderRequest(101L)));

        verifyNoInteractions(accountQueryMapper, orderMapper);
    }

    @Test
    void cancelOrderThatDoesNotExistReturnsNotFound() {
        when(orderMapper.findAccountIdByOrderId(anyLong())).thenReturn(null);

        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(101L, ORDER_UUID));

        verify(orderMapper, never()).updateOrderStatusConditionally(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void cancelOrderOwnedByAnotherAccountIsRejectedWithoutUpdating() {
        when(orderMapper.findAccountIdByOrderId(anyLong())).thenReturn(202L);

        assertThrows(AccountAccessDeniedException.class, () -> orderService.cancelOrder(101L, ORDER_UUID));

        verify(orderMapper, never()).updateOrderStatusConditionally(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void cancelOrderNotInNewStateReturnsConflict() {
        when(orderMapper.findAccountIdByOrderId(anyLong())).thenReturn(101L);
        when(orderMapper.updateOrderStatusConditionally(anyLong(), eq(101L), eq("NEW"), eq("CANCELLED"), any()))
            .thenReturn(0);

        OrderConflictException exception = assertThrows(
            OrderConflictException.class, () -> orderService.cancelOrder(101L, ORDER_UUID));
        assertEquals("ORD-409", exception.getCode());
    }

    @Test
    void cancelOwnNewOrderScopesUpdateToAuthenticatedAccount() {
        OrderResponse cancelled = new OrderResponse(
            "ORD-" + ORDER_UUID, OrderStatus.CANCELLED, "Order cancelled", "INFY.NS", OrderSide.BUY, 10, BigDecimal.TEN);
        when(orderMapper.findAccountIdByOrderId(anyLong())).thenReturn(101L);
        when(orderMapper.updateOrderStatusConditionally(anyLong(), eq(101L), eq("NEW"), eq("CANCELLED"), any()))
            .thenReturn(1);
        when(orderMapper.findOrderById(anyLong(), eq("ORD-" + ORDER_UUID))).thenReturn(cancelled);

        assertSame(cancelled, orderService.cancelOrder(101L, ORDER_UUID));
    }

    private static PlaceOrderRequest orderRequest(long accountId) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(accountId);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("1000.00"));
        request.setIdempotencyKey("idem-version-mismatch");
        return request;
    }
}