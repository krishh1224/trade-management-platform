package com.team5.strategies.indicators;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lines a strategy's rule is decided on, one value per price (oldest first), for drawing. Each
 * value is computed exactly as the rule computes it at that price, so the chart and the decisions
 * agree: a crossing on the chart is a crossing the rule saw. A value is null where the window does
 * not fit yet.
 */
public final class IndicatorSeries {

    private static final int SCALE = 4;

    private IndicatorSeries() {
    }

    // the simple moving average over the window ending at each price (MovingAverageCrossover)
    public static List<BigDecimal> movingAverage(List<BigDecimal> prices, int window) {
        List<BigDecimal> values = new ArrayList<>(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            values.add(i + 1 >= window ? round(Indicators.mean(prices, i + 1, window)) : null);
        }
        return Collections.unmodifiableList(values);
    }

    // the band each price is measured against: from the window of prices before it (BollingerBands)
    public static Bands bollinger(List<BigDecimal> prices, int window, BigDecimal width) {
        List<BigDecimal> middle = new ArrayList<>(prices.size());
        List<BigDecimal> upper = new ArrayList<>(prices.size());
        List<BigDecimal> lower = new ArrayList<>(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            if (i >= window) {
                BigDecimal mean = Indicators.mean(prices, i, window);
                BigDecimal spread = Indicators.standardDeviation(prices, i, window).multiply(width, Indicators.MC);
                middle.add(round(mean));
                upper.add(round(mean.add(spread, Indicators.MC)));
                lower.add(round(mean.subtract(spread, Indicators.MC)));
            } else {
                middle.add(null);
                upper.add(null);
                lower.add(null);
            }
        }
        return new Bands(Collections.unmodifiableList(middle), Collections.unmodifiableList(upper),
            Collections.unmodifiableList(lower));
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record Bands(List<BigDecimal> middle, List<BigDecimal> upper, List<BigDecimal> lower) {
    }
}
