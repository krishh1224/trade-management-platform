package com.team5.strategies.indicators;

import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyRow;
import java.math.BigDecimal;
import java.util.List;

// Picks the rule a strategy is configured with. Pure: no clock, no database, no order.
public final class StrategySignals {

    private StrategySignals() {
    }

    public static int requiredPrices(StrategyRow strategy) {
        return switch (strategy.getStrategyType()) {
            case MA_CROSSOVER -> MovingAverageCrossover.requiredPrices(strategy.getLongWindow());
            case BOLLINGER_BANDS -> BollingerBands.requiredPrices(strategy.getBandWindow());
        };
    }

    public static SignalResult evaluate(StrategyRow strategy, List<BigDecimal> pricesOldestFirst) {
        return switch (strategy.getStrategyType()) {
            case MA_CROSSOVER -> MovingAverageCrossover.evaluate(
                pricesOldestFirst, strategy.getShortWindow(), strategy.getLongWindow());
            case BOLLINGER_BANDS -> BollingerBands.evaluate(
                pricesOldestFirst, strategy.getBandWindow(), strategy.getBandWidth());
        };
    }
}
