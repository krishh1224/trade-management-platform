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
                orderMapper, portfolioMapper, accountMapper, instrumentMapper, quoteClient,
                new CurrencyConversionService(quoteClient)
        );
    }

    // ==================== Currency conversion (USD instrument, INR account) ====================

    private static final Quote USDINR = new Quote("FX:USDINR", new BigDecimal("96.169038"), new BigDecimal("96.170962"));
    private static final Quote AAPL = new Quote("AAPL", new BigDecimal("333.65"), new BigDecimal("333.73"));

    private void stubUsdOrder(long orderId, String side, int quantity, String limit, String inrBalance) {
        long portfolioId = 900L;
        long accountId = 901L;
        long instrumentId = 307L;
        when(orderMapper.findById(orderId)).thenReturn(new OrderRecord(
                orderId, portfolioId, instrumentId, quantity, new BigDecimal(limit), "NEW", side, "fx-" + orderId,
                LocalDateTime.now(), LocalDateTime.now(), null));
        when(portfolioMapper.findById(portfolioId)).thenReturn(new PortfolioRecord(
                portfolioId, "Main", accountId, "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));
        when(accountMapper.findById(accountId)).thenReturn(new AccountRecord(
                accountId, 1L, "1000000101", "LOW", "INR", new BigDecimal(inrBalance), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()));
        when(instrumentMapper.findById(instrumentId)).thenReturn(new InstrumentRecord(
                instrumentId, "AAPL", "Apple Inc.", "EQUITY", "USD", true));
    }

    @Test
    void usdBuyOnInrAccount_FillsAndSettlesInInrAtFxAsk() {
        stubUsdOrder(20L, "BUY", 2, "340.00", "98000.00");
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);
        when(quoteClient.getQuote("AAPL")).thenReturn(AAPL);

        ExecutionResult result = executionService.executeOrder(20L);

        assertEquals(ExecutionStatus.FILLED, result.getStatus());
        assertEquals(new BigDecimal("333.73"), result.getExecutionPrice()); // still in USD
        assertEquals(new BigDecimal("96.17096200"), result.getFxRate());    // ask side
        assertEquals("INR", result.getSettlementCurrency());
        // 2 x 333.73 USD = 667.46 USD x 96.170962 = 64190.2703 -> 64190.27 INR
        assertEquals(new BigDecimal("64190.27"), result.getSettlementAmount());
    }

    @Test
    void usdBuyOnInrAccount_RejectsWhenInrCostExceedsBalance() {
        // Limit cost is 680 USD, which a raw comparison against 60000 would pass.
        // Converted it is 680 x 96.170962 = 65396.25 INR > 60000 INR.
        stubUsdOrder(21L, "BUY", 2, "340.00", "60000.00");
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);

        ExecutionResult result = executionService.executeOrder(21L);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertTrue(result.getReason().contains("Insufficient funds"));
        assertTrue(result.getReason().contains("65396.25 INR"));
        verify(quoteClient, never()).getQuote(any());
    }

    @Test
    void usdSellOnInrAccount_SettlesAtFxBid() {
        stubUsdOrder(22L, "SELL", 2, "330.00", "1000.00");
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);
        when(quoteClient.getQuote("AAPL")).thenReturn(AAPL);

        ExecutionResult result = executionService.executeOrder(22L);

        assertEquals(ExecutionStatus.FILLED, result.getStatus());
        assertEquals(new BigDecimal("333.65"), result.getExecutionPrice());
        assertEquals(new BigDecimal("96.16903800"), result.getFxRate());    // bid side
        // 2 x 333.65 USD = 667.30 USD x 96.169038 = 64173.5991 -> 64173.60 INR
        assertEquals(new BigDecimal("64173.60"), result.getSettlementAmount());
    }

    @Test
    void usdOrder_RejectsWhenFxRateUnavailable() {
        stubUsdOrder(23L, "BUY", 2, "340.00", "98000.00");
        when(quoteClient.getFxRate("USD", "INR")).thenThrow(new QuoteUnavailableException("FX down"));

        ExecutionResult result = executionService.executeOrder(23L);

        assertEquals(ExecutionStatus.REJECTED, result.getStatus());
        assertNull(result.getExecutionPrice());
        assertTrue(result.getReason().startsWith("FX rate unavailable"));
        verify(quoteClient, never()).getQuote(any());
    }

    @Test
    void sameCurrencyOrder_NeverCallsFxAndSettlesAtPriceTimesQuantity() {
        long orderId = 24L;
        when(orderMapper.findById(orderId)).thenReturn(new OrderRecord(
                orderId, 902L, 301L, 10, new BigDecimal("500.00"), "NEW", "BUY", "inr-24",
                LocalDateTime.now(), LocalDateTime.now(), null));
        when(portfolioMapper.findById(902L)).thenReturn(new PortfolioRecord(
                902L, "Main", 903L, "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));
        when(accountMapper.findById(903L)).thenReturn(new AccountRecord(
                903L, 1L, "1000000103", "LOW", "INR", new BigDecimal("10000.00"), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()));
        when(instrumentMapper.findById(301L)).thenReturn(new InstrumentRecord(
                301L, "INFY.NS", "Infosys", "EQUITY", "INR", true));
        when(quoteClient.getQuote("INFY.NS")).thenReturn(
                new Quote("INFY.NS", new BigDecimal("490.00"), new BigDecimal("499.00")));

        ExecutionResult result = executionService.executeOrder(orderId);

        assertEquals(ExecutionStatus.FILLED, result.getStatus());
        assertEquals(0, BigDecimal.ONE.compareTo(result.getFxRate()));
        assertEquals("INR", result.getSettlementCurrency());
        assertEquals(new BigDecimal("4990.00"), result.getSettlementAmount());
        verify(quoteClient, never()).getFxRate(any(), any());
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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("10000.00"), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", true
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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("5000.00"), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", true
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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("10000.00"), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", true
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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("1000.00"), "ACTIVE", 1L,  // insufficient balance
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", true
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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("10000.00"), "SUSPENDED", 1L,  // account is suspended
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", true
        );

        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("10000.00"), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", true
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
                accountId, 1L, "1234567890", "CONSERVATIVE", "INR",
                new BigDecimal("10000.00"), "ACTIVE", 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );

        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", false  // not tradable
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

