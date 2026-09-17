package com.neueda.learning.service;

import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.domain.enums.ExecutionStatus;
import com.neueda.learning.exception.QuoteUnavailableException;
import com.neueda.learning.mapper.AccountMapper;
import com.neueda.learning.mapper.InstrumentMapper;
import com.neueda.learning.mapper.OrderMapper;
import com.neueda.learning.mapper.PortfolioMapper;
import com.neueda.learning.model.AccountRecord;
import com.neueda.learning.model.InstrumentRecord;
import com.neueda.learning.model.OrderRecord;
import com.neueda.learning.model.PortfolioRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TradeExecutionService.
 * Tests the orchestration logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class TradeExecutionServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PortfolioMapper portfolioMapper;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private FauxnanceQuoteClient quoteClient;

    private TradeExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new TradeExecutionService(
                orderMapper, portfolioMapper, accountMapper, instrumentMapper, quoteClient
        );
    }

    /**
     * Test 1: BUY order marketable + sufficient funds → FILLED
     */
    @Test
    void testBuyOrderAffordableAndMarketable_ShouldFill() {
        long orderId = 1L;
        long portfolioId = 100L;
        long accountId = 200L;
        long instrumentId = 300L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key1",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("10000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        Quote quote = new Quote("INFY.NS", new BigDecimal("490.00"), new BigDecimal("499.00"));

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(quoteClient.getQuote("INFY.NS")).thenReturn(quote);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.FILLED, result.getStatus());
        assertEquals(new BigDecimal("499.00"), result.getExecutionPrice());
        assertEquals(orderId, result.getOrderId());
    }

    /**
     * Test 2: SELL order marketable → FILLED
     */
    @Test
    void testSellOrderMarketable_ShouldFill() {
        long orderId = 2L;
        long portfolioId = 101L;
        long accountId = 201L;
        long instrumentId = 301L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 5,
                new BigDecimal("500.00"), "NEW", "SELL", "key2",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("5000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        Quote quote = new Quote("INFY.NS", new BigDecimal("501.00"), new BigDecimal("510.00"));

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(quoteClient.getQuote("INFY.NS")).thenReturn(quote);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.FILLED, result.getStatus());
        assertEquals(new BigDecimal("501.00"), result.getExecutionPrice());
    }

    /**
     * Test 3: Non-marketable order → REJECTED
     */
    @Test
    void testNonMarketableOrder_ShouldReject() {
        long orderId = 3L;
        long portfolioId = 102L;
        long accountId = 202L;
        long instrumentId = 302L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("490.00"), "NEW", "BUY", "key3",  // limit too low
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("10000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        Quote quote = new Quote("INFY.NS", new BigDecimal("500.00"), new BigDecimal("510.00"));  // ask > limit

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(quoteClient.getQuote("INFY.NS")).thenReturn(quote);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertNull(result.getExecutionPrice());
        assertTrue(result.getReason().contains("non-marketable"));
    }

    /**
     * Test 4: Account cannot afford BUY at execution price → REJECTED
     */
    @Test
    void testInsufficientFunds_ShouldReject() {
        long orderId = 4L;
        long portfolioId = 103L;
        long accountId = 203L;
        long instrumentId = 303L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 100,  // large quantity
                new BigDecimal("500.00"), "NEW", "BUY", "key4",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("1000.00"), "ACTIVE",  // insufficient balance
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertTrue(result.getReason().contains("Insufficient funds"));
    }

    /**
     * Test 5: Account becomes FROZEN/SUSPENDED after acceptance → does not trade
     */
    @Test
    void testAccountSuspended_ShouldReject() {
        long orderId = 5L;
        long portfolioId = 104L;
        long accountId = 204L;
        long instrumentId = 304L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key5",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("10000.00"), "SUSPENDED",  // account is suspended
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertTrue(result.getReason().contains("not active"));
    }

    /**
     * Test 6: Quote unavailable after retries → order resolved as REJECTED
     */
    @Test
    void testQuoteUnavailableAfterRetries_ShouldReject() {
        long orderId = 6L;
        long portfolioId = 105L;
        long accountId = 205L;
        long instrumentId = 305L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key6",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("10000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(quoteClient.getQuote("INFY.NS"))
                .thenThrow(new QuoteUnavailableException("Service unavailable"));

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertNull(result.getExecutionPrice());
        assertTrue(result.getReason().contains("Quote unavailable"));
    }

    /**
     * Test: Order already FILLED (terminal) → skip execution
     */
    @Test
    void testAlreadyFilledOrder_SkipsExecution() {
        long orderId = 7L;

        OrderRecord order = new OrderRecord(
                orderId, 100L, 300L, 10,
                new BigDecimal("500.00"), "FILLED", "BUY", "key7",  // already FILLED
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        when(orderMapper.findById(orderId)).thenReturn(order);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertTrue(result.getReason().contains("already FILLED"));
        // Mappers should not be called beyond orderMapper
        verify(portfolioMapper, never()).findById(any());
    }

    /**
     * Test: Instrument not tradable → REJECTED
     */
    @Test
    void testNonTradableInstrument_ShouldReject() {
        long orderId = 8L;
        long portfolioId = 106L;
        long accountId = 206L;
        long instrumentId = 306L;

        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key8",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        AccountRecord account = new AccountRecord(
                accountId, 1L, "1234567890", "CONSERVATIVE", "USD",
                new BigDecimal("10000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", false  // not tradable
        );

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertTrue(result.getReason().contains("not tradable"));
    }
}

