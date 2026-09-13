package com.team5.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.OrderSide;
import com.team5.exception.OrderConflictException;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.HoldingMapper;
import com.team5.mapper.OrderMapper;
import com.team5.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

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
        orderService = new OrderServiceImpl(accountQueryMapper, orderMapper, holdingMapper, transactionMapper);
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
        when(accountQueryMapper.updateBalanceWithVersion(
            org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(0);

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(101L);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("1000.00"));
        request.setIdempotencyKey("idem-version-mismatch");

        OrderConflictException exception = assertThrows(OrderConflictException.class, () -> orderService.placeOrder(request));
        assertEquals("ORD-409", exception.getCode());

        verifyNoInteractions(holdingMapper, transactionMapper);
    }
}
