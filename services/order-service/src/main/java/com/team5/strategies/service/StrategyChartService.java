package com.team5.strategies.service;

import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.StrategyType;
import com.team5.strategies.dto.StrategyChartResponse;
import com.team5.strategies.dto.StrategyChartResponse.Marker;
import com.team5.strategies.dto.StrategyChartResponse.Point;
import com.team5.strategies.dto.StrategyResponse;
import com.team5.strategies.indicators.BollingerBands;
import com.team5.strategies.indicators.IndicatorSeries;
import com.team5.strategies.indicators.MovingAverageCrossover;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper.StoredQuote;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chart for one strategy: the prices its symbol has had, the lines its rule compares, and where it
 * fired. Read-only; the same account checks as every other strategy route (through
 * {@link StrategyService#get}). Only quotes still stored are drawn, so firings on quotes that have
 * been pruned are left off the chart (they stay in the activity list).
 */
@Service
public class StrategyChartService {

    // as many quotes as are kept per symbol
    static final int MAX_POINTS = 250;

    private final StrategyService strategyService;
    private final StrategyQuoteMapper quoteMapper;
    private final StrategyExecutionMapper executionMapper;

    public StrategyChartService(
        StrategyService strategyService,
        StrategyQuoteMapper quoteMapper,
        StrategyExecutionMapper executionMapper
    ) {
        this.strategyService = strategyService;
        this.quoteMapper = quoteMapper;
        this.executionMapper = executionMapper;
    }

    @Transactional(readOnly = true)
    public StrategyChartResponse chart(long authenticatedAccountId, long accountId, long strategyId) {
        StrategyResponse strategy = strategyService.get(authenticatedAccountId, accountId, strategyId);

        List<StoredQuote> quotes = new ArrayList<>(quoteMapper.findRecentQuotes(strategy.symbol(), MAX_POINTS));
        Collections.reverse(quotes);
        List<BigDecimal> prices = quotes.stream().map(StoredQuote::getPrice).toList();

        List<Point> points = new ArrayList<>(quotes.size());
        int required;
        if (strategy.type() == StrategyType.MA_CROSSOVER) {
            required = MovingAverageCrossover.requiredPrices(strategy.longWindow());
            List<BigDecimal> shortAverage = IndicatorSeries.movingAverage(prices, strategy.shortWindow());
            List<BigDecimal> longAverage = IndicatorSeries.movingAverage(prices, strategy.longWindow());
            for (int i = 0; i < quotes.size(); i++) {
                points.add(new Point(quotes.get(i).getQuotedAt(), prices.get(i), shortAverage.get(i), longAverage.get(i),
                    null, null, null));
            }
        } else {
            required = BollingerBands.requiredPrices(strategy.bandWindow());
            IndicatorSeries.Bands bands = IndicatorSeries.bollinger(prices, strategy.bandWindow(), strategy.bandWidth());
            for (int i = 0; i < quotes.size(); i++) {
                points.add(new Point(quotes.get(i).getQuotedAt(), prices.get(i), null, null,
                    bands.middle().get(i), bands.upper().get(i), bands.lower().get(i)));
            }
        }

        Map<UUID, Integer> indexByQuote = new HashMap<>();
        for (int i = 0; i < quotes.size(); i++) {
            indexByQuote.put(quotes.get(i).getEventId(), i);
        }
        List<Marker> markers = new ArrayList<>();
        for (ExecutionRow execution : executionMapper.findByStrategy(strategyId, MAX_POINTS)) {
            Integer index = indexByQuote.get(execution.getQuoteEventId());
            if (index != null) {
                markers.add(new Marker(index, quotes.get(index).getQuotedAt(), execution.getQuotePrice(),
                    execution.getSignal(), execution.getOutcome(), execution.getOrderId(), execution.getDetail()));
            }
        }
        markers.sort((a, b) -> Integer.compare(a.pointIndex(), b.pointIndex()));

        return new StrategyChartResponse(strategyId, strategy.symbol(), strategy.type(), required,
            List.copyOf(points), List.copyOf(markers));
    }
}
