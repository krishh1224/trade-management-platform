package com.team5.strategies.engine;

import static com.team5.strategies.StrategyFixtures.OWNER;
import static com.team5.strategies.StrategyFixtures.SYMBOL;
import static com.team5.strategies.StrategyFixtures.T0;
import static com.team5.strategies.StrategyFixtures.defaultProperties;
import static com.team5.strategies.StrategyFixtures.maCrossover;
import static com.team5.strategies.StrategyFixtures.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.OrderSide;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.PositionSnapshot;
import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyReadMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// The controls between a fired signal and an order, each checked at the point of decision.
class StrategyExecutorTest {

    private static final SignalResult BUY = SignalResult.buy("Golden cross: test");
    private static final SignalResult SELL = SignalResult.sell("Death cross: test");

    private StrategyMapper strategyMapper;
    private StrategyExecutionMapper executionMapper;
    private StrategyReadMapper readMapper;
    private TradeApiOrderClient orderClient;
    private StrategyExecutor executor;

    @BeforeEach
    void setUp() {
        strategyMapper = mock(StrategyMapper.class);
        executionMapper = mock(StrategyExecutionMapper.class);
        readMapper = mock(StrategyReadMapper.class);
        orderClient = mock(TradeApiOrderClient.class);
        executor = new StrategyExecutor(strategyMapper, executionMapper, readMapper, orderClient,
            defaultProperties(), Clock.fixed(T0.toInstant(), ZoneOffset.UTC));
        when(readMapper.findPosition(OWNER, SYMBOL)).thenReturn(new PositionSnapshot(0, 0, 0));
    }

    @Test
    void firedBuy_placesTheOrderThroughTheTradeApi_forTheStrategysAccount() {
        StrategyRow strategy = locked(maCrossover(7L));
        StrategyQuote quote = quote("1500.00");
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.accepted(200, "ORD-abc"));

        ExecutionRow execution = executor.execute(7L, BUY, quote);

        ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(orderClient).place(eq(7L), request.capture());
        assertEquals(OWNER, request.getValue().getAccountId());
        assertEquals(SYMBOL, request.getValue().getSymbol());
        assertEquals(OrderSide.BUY, request.getValue().getSide());
        assertEquals(10, request.getValue().getQuantity());
        // 1% over the ask (1500.50), rounded up to the paisa
        assertEquals(new BigDecimal("1515.51"), request.getValue().getPrice());
        assertEquals("strat-7-" + quote.eventId(), request.getValue().getIdempotencyKey());

        assertEquals(ExecutionOutcome.PLACED, execution.getOutcome());
        assertEquals("ORD-abc", execution.getOrderId());
        verify(executionMapper).insert(execution);
        // 10 x 1515.51 counted against the spending limit, the failure run cleared
        verify(strategyMapper).recordPlaced(eq(7L), eq(new BigDecimal("15155.10")), any());
        assertEquals(strategy.getAccountId(), request.getValue().getAccountId());
    }

    @Test
    void strategyDisabledAfterTheCycleLoadedIt_placesNothing() {
        // the engine loaded it ENABLED; by the time the decision takes the row lock it is DISABLED
        StrategyRow strategy = maCrossover(7L);
        strategy.setStatus(StrategyStatus.DISABLED);
        locked(strategy);

        ExecutionRow execution = executor.execute(7L, BUY, quote("1500.00"));

        verifyNoInteractions(orderClient);
        assertEquals(ExecutionOutcome.SKIPPED_DISABLED, execution.getOutcome());
        assertTrue(execution.getDetail().contains("DISABLED"), execution.getDetail());
        verify(executionMapper).insert(execution);
        verify(strategyMapper, never()).recordPlaced(anyLong(), any(), any());
    }

    @Test
    void haltedStrategy_placesNothing() {
        StrategyRow strategy = maCrossover(7L);
        strategy.setStatus(StrategyStatus.HALTED);
        locked(strategy);

        assertEquals(ExecutionOutcome.SKIPPED_DISABLED, executor.execute(7L, BUY, quote("1500.00")).getOutcome());
        verifyNoInteractions(orderClient);
    }

    @Test
    void deletedStrategy_placesNothingAndRecordsNothing() {
        when(strategyMapper.lockById(7L)).thenReturn(null);

        assertNull(executor.execute(7L, BUY, quote("1500.00")));
        verifyNoInteractions(orderClient);
        verify(executionMapper, never()).insert(any());
    }

    @Test
    void quoteAlreadyActedOn_placesNothingTwice() {
        locked(maCrossover(7L));
        StrategyQuote quote = quote("1500.00");
        when(executionMapper.exists(7L, quote.eventId())).thenReturn(true);

        assertNull(executor.execute(7L, BUY, quote));
        verifyNoInteractions(orderClient);
        verify(executionMapper, never()).insert(any());
    }

    @Test
    void buyOverTheRemainingSpend_placesNothing() {
        StrategyRow strategy = maCrossover(7L);
        strategy.setMaxSpend(new BigDecimal("20000.00"));
        strategy.setSpent(new BigDecimal("5000.00"));
        locked(strategy);

        ExecutionRow execution = executor.execute(7L, BUY, quote("1500.00"));

        verifyNoInteractions(orderClient);
        assertEquals(ExecutionOutcome.SKIPPED_SPEND_LIMIT, execution.getOutcome());
        assertTrue(execution.getDetail().contains("15155.10"), execution.getDetail());
        assertTrue(execution.getDetail().contains("15000.00"), execution.getDetail());
        verify(strategyMapper).recordTriggered(eq(7L), any());
    }

    @Test
    void buyExactlyUsingUpTheSpend_isPlaced() {
        StrategyRow strategy = maCrossover(7L);
        strategy.setMaxSpend(new BigDecimal("15155.10"));
        locked(strategy);
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.accepted(200, "ORD-abc"));

        assertEquals(ExecutionOutcome.PLACED, executor.execute(7L, BUY, quote("1500.00")).getOutcome());
    }

    @Test
    void buyPastTheMaxPosition_countingPendingBuys_placesNothing() {
        locked(maCrossover(7L));
        // 30 held + 15 on the way + 10 more = 55 > 50
        when(readMapper.findPosition(OWNER, SYMBOL)).thenReturn(new PositionSnapshot(30, 15, 0));

        ExecutionRow execution = executor.execute(7L, BUY, quote("1500.00"));

        verifyNoInteractions(orderClient);
        assertEquals(ExecutionOutcome.SKIPPED_POSITION_LIMIT, execution.getOutcome());
        assertTrue(execution.getDetail().contains("55"), execution.getDetail());
    }

    @Test
    void sellWithNothingHeld_placesNothing() {
        locked(maCrossover(7L));

        ExecutionRow execution = executor.execute(7L, SELL, quote("1500.00"));

        verifyNoInteractions(orderClient);
        assertEquals(ExecutionOutcome.SKIPPED_NO_HOLDING, execution.getOutcome());
    }

    @Test
    void sell_neverMoreThanIsLeftToSell() {
        locked(maCrossover(7L));
        // 6 held, 2 already being sold: 4 left of the 10 the strategy would sell
        when(readMapper.findPosition(OWNER, SYMBOL)).thenReturn(new PositionSnapshot(6, 0, 2));
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.accepted(200, "ORD-s"));

        executor.execute(7L, SELL, quote("1500.00"));

        ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(orderClient).place(eq(7L), request.capture());
        assertEquals(4, request.getValue().getQuantity());
        assertEquals(OrderSide.SELL, request.getValue().getSide());
        // 1% under the bid (1499.50), rounded down
        assertEquals(new BigDecimal("1484.50"), request.getValue().getPrice());
        // a sale spends nothing
        verify(strategyMapper).recordPlaced(eq(7L), eq(BigDecimal.ZERO), any());
    }

    @Test
    void rejectedOrder_isAFailure_thatDoesNotHaltBeforeTheLimit() {
        StrategyRow strategy = maCrossover(7L);
        strategy.setConsecutiveFailures(1);
        locked(strategy);
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.rejected(422, "ACC-403", "Trade API 403 ACC-403"));

        ExecutionRow execution = executor.execute(7L, BUY, quote("1500.00"));

        assertEquals(ExecutionOutcome.FAILED, execution.getOutcome());
        // 2 of 3: no halt reason, and a refused order spent nothing
        verify(strategyMapper).recordFailure(eq(7L), eq(BigDecimal.ZERO), isNull(), any());
    }

    @Test
    void failureReachingTheLimit_haltsTheStrategy() {
        StrategyRow strategy = maCrossover(7L);
        strategy.setConsecutiveFailures(2);
        locked(strategy);
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.rejected(503, null, "Trade API 503"));

        executor.execute(7L, BUY, quote("1500.00"));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(strategyMapper).recordFailure(eq(7L), any(), reason.capture(), any());
        assertTrue(reason.getValue().startsWith("Halted after 3 failed orders in a row"), reason.getValue());
    }

    @Test
    void duplicateKey_holdingOurOrder_countsAsPlaced() {
        locked(maCrossover(7L));
        StrategyQuote quote = quote("1500.00");
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.duplicate("ORD-409", "Trade API 409 ORD-409"));
        when(readMapper.findOrderIdByIdempotencyKey("strat-7-" + quote.eventId())).thenReturn("ORD-earlier");

        ExecutionRow execution = executor.execute(7L, BUY, quote);

        assertEquals(ExecutionOutcome.PLACED, execution.getOutcome());
        assertEquals("ORD-earlier", execution.getOrderId());
        verify(strategyMapper).recordPlaced(eq(7L), eq(new BigDecimal("15155.10")), any());
    }

    @Test
    void duplicateKey_withNoOrderBehindIt_isAFailure() {
        locked(maCrossover(7L));
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.duplicate("ORD-409", "Trade API 409 ORD-409"));

        assertEquals(ExecutionOutcome.FAILED, executor.execute(7L, BUY, quote("1500.00")).getOutcome());
        verify(strategyMapper).recordFailure(eq(7L), eq(BigDecimal.ZERO), isNull(), any());
    }

    @Test
    void noAnswerFromTheTradeApi_isAFailure_thatStillCountsTheValueAgainstTheLimit() {
        locked(maCrossover(7L));
        when(orderClient.place(eq(7L), any())).thenReturn(OrderPlacement.uncertain(0, "No answer"));

        ExecutionRow execution = executor.execute(7L, BUY, quote("1500.00"));

        assertEquals(ExecutionOutcome.FAILED, execution.getOutcome());
        // the order may exist: never risk spending past the limit
        verify(strategyMapper).recordFailure(eq(7L), eq(new BigDecimal("15155.10")), isNull(), any());
    }

    @Test
    void quoteWithoutBidOrAsk_pricesFromTheQuotePrice() {
        StrategyQuote quote = new StrategyQuote(java.util.UUID.randomUUID(), SYMBOL, new BigDecimal("100.00"), null, null, T0);

        assertEquals(new BigDecimal("101.00"), executor.limitPrice(OrderSide.BUY, quote));
        assertEquals(new BigDecimal("99.00"), executor.limitPrice(OrderSide.SELL, quote));
    }

    private StrategyRow locked(StrategyRow strategy) {
        when(strategyMapper.lockById(strategy.getStrategyId())).thenReturn(strategy);
        return strategy;
    }
}
