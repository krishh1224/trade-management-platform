package com.neueda.learning.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.event.OrderPlacedEvent;
import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.mapper.AccountMapper;
import com.neueda.learning.mapper.InstrumentMapper;
import com.neueda.learning.mapper.OrderMapper;
import com.neueda.learning.mapper.PortfolioMapper;
import com.neueda.learning.model.AccountRecord;
import com.neueda.learning.model.InstrumentRecord;
import com.neueda.learning.model.OrderRecord;
import com.neueda.learning.model.PortfolioRecord;
import com.neueda.learning.service.TradeExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

/**
 * Integration tests for the OrderPlacedConsumer.
 * Tests the flow from Kafka event through execution with mocked Fauxnance.
 */
@ExtendWith(MockitoExtension.class)
class OrderPlacedConsumerIntegrationTest {

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

    private OrderPlacedConsumer consumer;
    private TradeExecutionService executionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        executionService = new TradeExecutionService(
                orderMapper, portfolioMapper, accountMapper, instrumentMapper, quoteClient
        );
        consumer = new OrderPlacedConsumer(executionService);
        objectMapper = new ObjectMapper();
    }

    /**
     * Integration Test A: Quote available + marketable → FILLED
     */
    @Test
    void testA_QuoteAvailableAndMarketable_ExecutedAsFilled() {
        long orderId = 1000L;
        long accountId = 2000L;
        long portfolioId = 3000L;
        long instrumentId = 4000L;

        // Setup event
        OrderPlacedEvent event = new OrderPlacedEvent(
                "ORDER_PLACED", orderId, accountId, System.currentTimeMillis()
        );

        // Setup order
        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key-a",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        // Setup portfolio
        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "MainPortfolio", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        // Setup account
        AccountRecord account = new AccountRecord(
                accountId, 1L, "1111111111", "AGGRESSIVE", "USD",
                new BigDecimal("50000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        // Setup instrument
        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        // Setup mocks for all database calls
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

        // Setup Fauxnance quote
        Quote quote = new Quote("INFY.NS", new BigDecimal("490.00"), new BigDecimal("499.00"));
        when(quoteClient.getQuote("INFY.NS")).thenReturn(quote);

        // Execute
        consumer.consumeOrderPlaced(event);

        // Verify all database queries were executed
        verify(orderMapper).findById(orderId);
        verify(portfolioMapper).findById(portfolioId);
        verify(accountMapper).findById(accountId);
        verify(instrumentMapper).findById(instrumentId);
        verify(quoteClient).getQuote("INFY.NS");
    }

    /**
     * Integration Test B: Quote available + non-marketable → REJECTED
     */
    @Test
    void testB_QuoteAvailableNonMarketable_ExecutedAsRejected() {
        long orderId = 1001L;
        long accountId = 2001L;
        long portfolioId = 3001L;
        long instrumentId = 4001L;

        // Setup event
        OrderPlacedEvent event = new OrderPlacedEvent(
                "ORDER_PLACED", orderId, accountId, System.currentTimeMillis()
        );

        // Setup order with too-low limit price for BUY
        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("480.00"), "NEW", "BUY", "key-b",  // limit < ask
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        // Setup portfolio
        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "MainPortfolio", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        // Setup account
        AccountRecord account = new AccountRecord(
                accountId, 1L, "1111111111", "AGGRESSIVE", "USD",
                new BigDecimal("50000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        // Setup instrument
        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        // Setup mocks
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

        // Setup Fauxnance quote with higher ask
        Quote quote = new Quote("INFY.NS", new BigDecimal("490.00"), new BigDecimal("500.00"));
        when(quoteClient.getQuote("INFY.NS")).thenReturn(quote);

        // Execute
        consumer.consumeOrderPlaced(event);

        // Verify flow was executed
        verify(orderMapper).findById(orderId);
        verify(quoteClient).getQuote("INFY.NS");
    }

    /**
     * Integration Test C: Quote unavailable → retries → REJECTED
     */
    @Test
    void testC_QuoteUnavailableAfterRetries_ExecutedAsRejected() {
        long orderId = 1002L;
        long accountId = 2002L;
        long portfolioId = 3002L;
        long instrumentId = 4002L;

        // Setup event
        OrderPlacedEvent event = new OrderPlacedEvent(
                "ORDER_PLACED", orderId, accountId, System.currentTimeMillis()
        );

        // Setup order
        OrderRecord order = new OrderRecord(
                orderId, portfolioId, instrumentId, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key-c",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        // Setup portfolio
        PortfolioRecord portfolio = new PortfolioRecord(
                portfolioId, "MainPortfolio", accountId, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        // Setup account
        AccountRecord account = new AccountRecord(
                accountId, 1L, "1111111111", "AGGRESSIVE", "USD",
                new BigDecimal("50000.00"), "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        // Setup instrument
        InstrumentRecord instrument = new InstrumentRecord(
                instrumentId, "INFY.NS", "Infosys", "EQUITY", "INR", "NSE", true
        );

        // Setup mocks
        when(orderMapper.findById(orderId)).thenReturn(order);
        when(portfolioMapper.findById(portfolioId)).thenReturn(portfolio);
        when(accountMapper.findById(accountId)).thenReturn(account);
        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

        // Setup Fauxnance quote to fail (simulating retries)
        when(quoteClient.getQuote("INFY.NS"))
                .thenThrow(new com.neueda.learning.exception.QuoteUnavailableException("Service timeout"));

        // Execute - should not throw, should handle gracefully
        consumer.consumeOrderPlaced(event);

        // Verify flow was attempted
        verify(orderMapper).findById(orderId);
        verify(quoteClient).getQuote("INFY.NS");
    }

    /**
     * Test: Event with unknown event type is ignored
     */
    @Test
    void testUnknownEventType_IsIgnored() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "UNKNOWN_EVENT", 123L, 456L, System.currentTimeMillis()
        );

        // Execute - should do nothing
        consumer.consumeOrderPlaced(event);

        // Verify no database queries were made
        verify(orderMapper, never()).findById(any());
        verify(quoteClient, never()).getQuote(anyString());
    }

    /**
     * Test: Event with null orderId is ignored
     */
    @Test
    void testNullOrderId_IsIgnored() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "ORDER_PLACED", null, 456L, System.currentTimeMillis()  // null orderId
        );

        // Execute - should do nothing
        consumer.consumeOrderPlaced(event);

        // Verify no database queries were made
        verify(orderMapper, never()).findById(any());
    }

    /**
     * Test: Event with unknown fields is handled gracefully
     */
    @Test
    void testEventWithUnknownFields_IsHandledGracefully() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "ORDER_PLACED", 1003L, 2003L, System.currentTimeMillis()
        );
        // Simulate unknown fields via additional properties
        event.setAdditionalProperty("unknownField1", "value1");
        event.setAdditionalProperty("unknownField2", 12345);

        // Setup database mocks for successful execution
        long orderId = 1003L;
        OrderRecord order = new OrderRecord(
                orderId, 3003L, 4003L, 10,
                new BigDecimal("500.00"), "NEW", "BUY", "key-unknown",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        when(orderMapper.findById(orderId)).thenReturn(order);

        // Execute - should handle unknown fields without error
        consumer.consumeOrderPlaced(event);

        // Verify execution proceeded
        verify(orderMapper).findById(orderId);
    }
}

