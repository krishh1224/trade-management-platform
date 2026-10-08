package com.team5.strategies.engine;

import static com.team5.strategies.StrategyFixtures.SYMBOL;
import static com.team5.strategies.StrategyFixtures.T0;
import static com.team5.strategies.StrategyFixtures.bollinger;
import static com.team5.strategies.StrategyFixtures.defaultProperties;
import static com.team5.strategies.StrategyFixtures.maCrossover;
import static com.team5.strategies.StrategyFixtures.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.OrderSide;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.engine.StrategyEngine.CycleResult;
import com.team5.strategies.engine.StrategyQuoteRecorder.Recorded;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// One evaluation cycle: quote in, rules evaluated, fired signals handed to the executor.
class StrategyEngineTest {

    private StrategyQuoteRecorder recorder;
    private StrategyQuoteMapper quoteMapper;
    private StrategyMapper strategyMapper;
    private StrategyExecutor executor;
    private StrategyEngine engine;

    @BeforeEach
    void setUp() {
        recorder = mock(StrategyQuoteRecorder.class);
        quoteMapper = mock(StrategyQuoteMapper.class);
        strategyMapper = mock(StrategyMapper.class);
        executor = mock(StrategyExecutor.class);
        engine = new StrategyEngine(recorder, quoteMapper, strategyMapper, executor, defaultProperties(),
            Clock.fixed(T0.plusSeconds(30).toInstant(), ZoneOffset.UTC));
        when(recorder.record(any())).thenReturn(Recorded.NEW);
    }

    @Test
    void quoteCompletingACrossover_triggersTheStrategy() {
        StrategyQuote quote = quote("14");
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of(maCrossover(1L)));
        storedPrices(12, 11, 10, 9, 14);
        when(executor.execute(eq(1L), any(), eq(quote))).thenReturn(execution(ExecutionOutcome.PLACED));

        CycleResult result = engine.onQuote(quote);

        ArgumentCaptor<SignalResult> signal = ArgumentCaptor.forClass(SignalResult.class);
        verify(executor).execute(eq(1L), signal.capture(), eq(quote));
        assertEquals(OrderSide.BUY, signal.getValue().side());
        assertEquals(1, result.fired());
        assertEquals(1, result.placed());
    }

    @Test
    void quoteWithoutACrossover_takesNoAction() {
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of(maCrossover(1L)));
        storedPrices(10, 11, 12, 13, 14);

        CycleResult result = engine.onQuote(quote("14"));

        verifyNoInteractions(executor);
        assertEquals(1, result.evaluated());
        assertEquals(0, result.fired());
    }

    @Test
    void notEnoughHistoryYet_takesNoAction() {
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of(maCrossover(1L)));
        storedPrices(9, 14);

        engine.onQuote(quote("14"));

        verifyNoInteractions(executor);
    }

    @Test
    void quoteFromABacklogAfterDowntime_isStoredButNotTradedOn() {
        // consumed an hour after it was published: older than the 5-minute default
        StrategyQuote old = quote(UUID.randomUUID(), "14", T0.minusHours(1));

        CycleResult result = engine.onQuote(old);

        verify(recorder).record(old);
        verify(strategyMapper, never()).findEnabledBySymbol(any());
        verifyNoInteractions(executor);
        assertTrue(result.tooOldToTrade());
    }

    @Test
    void staleQuote_evaluatesNothing() {
        when(recorder.record(any())).thenReturn(Recorded.STALE);

        CycleResult result = engine.onQuote(quote("14"));

        verify(strategyMapper, never()).findEnabledBySymbol(any());
        verifyNoInteractions(executor);
        assertEquals(Recorded.STALE, result.recorded());
    }

    @Test
    void redeliveredQuote_isEvaluatedAgain_theExecutorDecidesWhetherItWasActedOn() {
        when(recorder.record(any())).thenReturn(Recorded.REDELIVERED);
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of(maCrossover(1L)));
        storedPrices(12, 11, 10, 9, 14);

        engine.onQuote(quote("14"));

        verify(executor).execute(eq(1L), any(), any());
    }

    @Test
    void noStrategiesOnTheSymbol_readsNoPrices() {
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of());

        engine.onQuote(quote("14"));

        verify(quoteMapper, never()).findRecentPrices(any(), any(), anyInt());
        verifyNoInteractions(executor);
    }

    @Test
    void eachStrategyIsEvaluatedOnItsOwnRule_andOnlyFiredOnesReachTheExecutor() {
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of(maCrossover(1L), bollinger(2L)));
        storedPrices(10, 10, 10, 10, 10.2);

        engine.onQuote(quote("10.2"));

        // MA: before SMA2 10 = SMA4 10, now 10.1 > 10.05 -> BUY; bands: 10.2 above the flat band -> SELL
        ArgumentCaptor<SignalResult> signal = ArgumentCaptor.forClass(SignalResult.class);
        verify(executor).execute(eq(1L), signal.capture(), any());
        assertEquals(OrderSide.BUY, signal.getValue().side());
        verify(executor).execute(eq(2L), signal.capture(), any());
        assertEquals(OrderSide.SELL, signal.getValue().side());
    }

    @Test
    void readsJustEnoughHistoryForTheLongestRule() {
        when(strategyMapper.findEnabledBySymbol(SYMBOL)).thenReturn(List.of(maCrossover(1L), bollinger(2L)));
        storedPrices(10, 11, 12, 13, 14);

        StrategyQuote quote = quote("14");
        engine.onQuote(quote);

        // SMA(4) needs 5, bands(3) need 5
        verify(quoteMapper).findRecentPrices(SYMBOL, quote.quotedAt(), 5);
        verify(executor, never()).execute(anyLong(), any(), any());
    }

    private void storedPrices(double... oldestFirst) {
        List<BigDecimal> newestFirst = new ArrayList<>(Arrays.stream(oldestFirst).mapToObj(BigDecimal::valueOf).toList());
        Collections.reverse(newestFirst);
        when(quoteMapper.findRecentPrices(eq(SYMBOL), any(), anyInt())).thenReturn(newestFirst);
    }

    private static ExecutionRow execution(ExecutionOutcome outcome) {
        ExecutionRow row = new ExecutionRow();
        row.setOutcome(outcome);
        return row;
    }
}
