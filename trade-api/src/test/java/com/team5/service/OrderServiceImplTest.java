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
import com.team5.exception.AccountAccessDeniedException;
import com.team5.exception.OrderConflictException;
import com.team5.exception.OrderNotFoundException;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.HoldingMapper;
import com.team5.mapper.OrderMapper;
import com.team5.mapper.TransactionMapper;
import java.math.BigDecimal;
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

    @Mock
    private HoldingMapper holdingMapper;

    @Mock
    private TransactionMapper transactionMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
            accountQueryMapper, orderMapper, holdingMapper, transactionMapper, new AccountAccessAuthorizer());
    }

    @Test
    void concurrentUpdateIsDetectedThroughVersionMismatch() {
        Map<String, Object> account = new HashMap<>();
        account.put("acc_id", 101L);
        account.put("status", "ACTIVE");
        account.put("base_currency", "INR");
        account.put("balance", new BigDecimal("50000.00"));
        account.put("version", 7L);

        Map<String, Object> instrument = new HashMap<>();
        instrument.put("instrument_id", 301L);
        instrument.put("ticker", "INFY.NS");
        instrument.put("tradable", true);

        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);
        when(orderMapper.findInstrumentByTicker("INFY.NS")).thenReturn(instrument);
        when(orderMapper.findPortfolioIdByAccountId(101L)).thenReturn(201L);
        when(accountQueryMapper.updateBalanceWithVersion(eq(101L), any(), eq(7L), any())).thenReturn(0);

        OrderConflictException exception = assertThrows(
            OrderConflictException.class, () -> orderService.placeOrder(101L, orderRequest(101L)));
        assertEquals("ORD-409", exception.getCode());

        verifyNoInteractions(holdingMapper, transactionMapper);
    }

    @Test
    void placeOrderForAnotherAccountIsRejectedBeforeAnyDatabaseAccess() {
        assertThrows(AccountAccessDeniedException.class, () -> orderService.placeOrder(999L, orderRequest(101L)));

        verifyNoInteractions(accountQueryMapper, orderMapper, holdingMapper, transactionMapper);
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
