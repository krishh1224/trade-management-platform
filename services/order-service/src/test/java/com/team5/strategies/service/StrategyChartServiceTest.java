package com.team5.strategies.service;

import static com.team5.strategies.StrategyFixtures.OWNER;
import static com.team5.strategies.StrategyFixtures.SYMBOL;
import static com.team5.strategies.StrategyFixtures.T0;
import static com.team5.strategies.StrategyFixtures.bollinger;
import static com.team5.strategies.StrategyFixtures.maCrossover;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.dto.StrategyChartResponse;
import com.team5.strategies.dto.StrategyResponse;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper.StoredQuote;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrategyChartServiceTest {

    private StrategyService strategyService;
    private StrategyQuoteMapper quoteMapper;
    private StrategyExecutionMapper executionMapper;
    private StrategyChartService chartService;
    private List<StoredQuote> newestFirst;

    @BeforeEach
    void setUp() {
        strategyService = mock(StrategyService.class);
        quoteMapper = mock(StrategyQuoteMapper.class);
        executionMapper = mock(StrategyExecutionMapper.class);
        chartService = new StrategyChartService(strategyService, quoteMapper, executionMapper);

        // stored 1500, 1490, 1480, 1470, 1530 one minute apart; the mapper returns them newest first
        newestFirst = new ArrayList<>();
        double[] oldestFirst = {1500, 1490, 1480, 1470, 1530};
        for (int i = oldestFirst.length - 1; i >= 0; i--) {
            StoredQuote quote = new StoredQuote();
            quote.setQuotedAt(T0.plusMinutes(i));
            quote.setEventId(UUID.randomUUID());
            quote.setPrice(BigDecimal.valueOf(oldestFirst[i]));
            newestFirst.add(quote);
        }
        when(quoteMapper.findRecentQuotes(SYMBOL, 250)).thenReturn(newestFirst);
    }

    @Test
    void crossoverChart_hasThePricesOldestFirst_withBothAverages_andAMarkerWhereItFired() {
        when(strategyService.get(OWNER, OWNER, 7L)).thenReturn(StrategyResponse.from(maCrossover(7L)));
        UUID lastQuote = newestFirst.get(0).getEventId();
        when(executionMapper.findByStrategy(7L, 250)).thenReturn(List.of(
            execution(lastQuote, ExecutionOutcome.PLACED, "ORD-1"),
            execution(UUID.randomUUID(), ExecutionOutcome.SKIPPED_DISABLED, null)));   // its quote was pruned

        StrategyChartResponse chart = chartService.chart(OWNER, OWNER, 7L);

        assertEquals(5, chart.requiredPrices());
        assertEquals(5, chart.points().size());
        assertEquals(0, new BigDecimal("1500").compareTo(chart.points().get(0).price()));
        assertEquals(T0.plusMinutes(4), chart.points().get(4).quotedAt());
        assertNull(chart.points().get(0).shortAverage());
        // the cross: SMA2 1500 above SMA4 1492.5 on the last price
        assertEquals(new BigDecimal("1500.0000"), chart.points().get(4).shortAverage());
        assertEquals(new BigDecimal("1492.5000"), chart.points().get(4).longAverage());
        assertNull(chart.points().get(4).upperBand());

        assertEquals(1, chart.markers().size(), "a firing on a pruned quote is not drawn");
        assertEquals(4, chart.markers().get(0).pointIndex());
        assertEquals(OrderSide.BUY, chart.markers().get(0).signal());
        assertEquals("ORD-1", chart.markers().get(0).orderId());
    }

    @Test
    void bollingerChart_hasTheBandsInsteadOfTheAverages() {
        when(strategyService.get(OWNER, OWNER, 8L)).thenReturn(StrategyResponse.from(bollinger(8L)));

        StrategyChartResponse chart = chartService.chart(OWNER, OWNER, 8L);

        assertEquals(5, chart.requiredPrices());
        assertNull(chart.points().get(4).shortAverage());
        // the band for 1530 is from 1490, 1480, 1470
        assertEquals(new BigDecimal("1480.0000"), chart.points().get(4).middleBand());
        assertNull(chart.points().get(2).middleBand());
    }

    @Test
    void anotherAccountsStrategy_readsNothing() {
        when(strategyService.get(OWNER, OWNER, 9L)).thenThrow(new AccountAccessDeniedException());

        assertThrows(AccountAccessDeniedException.class, () -> chartService.chart(OWNER, OWNER, 9L));
        verifyNoInteractions(quoteMapper, executionMapper);
    }

    private static ExecutionRow execution(UUID quoteId, ExecutionOutcome outcome, String orderId) {
        ExecutionRow row = new ExecutionRow();
        row.setQuoteEventId(quoteId);
        row.setSignal(OrderSide.BUY);
        row.setOutcome(outcome);
        row.setQuotePrice(new BigDecimal("1530"));
        row.setOrderId(orderId);
        return row;
    }
}
