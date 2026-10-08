package com.team5.strategies.indicators;

import com.team5.strategies.domain.SignalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Simple moving-average crossover over the last prices consumed for a symbol, oldest first.
 *
 * <pre>
 *   BUY  (golden cross): short SMA was at or below the long SMA on the previous price, and is above it now
 *   SELL (death cross):  short SMA was at or above the long SMA on the previous price, and is below it now
 * </pre>
 *
 * Like a price alert, this is a crossing, not a state: while the short average stays above the long one
 * nothing fires again. It needs {@code longWindow + 1} prices, so the previous averages exist too.
 */
public final class MovingAverageCrossover {

    private MovingAverageCrossover() {
    }

    public static int requiredPrices(int longWindow) {
        return longWindow + 1;
    }

    public static SignalResult evaluate(List<BigDecimal> prices, int shortWindow, int longWindow) {
        if (shortWindow < 1 || longWindow <= shortWindow) {
            throw new IllegalArgumentException("need 1 <= shortWindow < longWindow, got " + shortWindow + "/" + longWindow);
        }
        int required = requiredPrices(longWindow);
        if (prices.size() < required) {
            return SignalResult.none("Waiting for price history: " + prices.size() + " of " + required + " prices");
        }

        int n = prices.size();
        BigDecimal previousShort = Indicators.mean(prices, n - 1, shortWindow);
        BigDecimal previousLong = Indicators.mean(prices, n - 1, longWindow);
        BigDecimal currentShort = Indicators.mean(prices, n, shortWindow);
        BigDecimal currentLong = Indicators.mean(prices, n, longWindow);

        int before = previousShort.compareTo(previousLong);
        int now = currentShort.compareTo(currentLong);
        String averages = "SMA(" + shortWindow + ") " + show(currentShort) + " vs SMA(" + longWindow + ") " + show(currentLong);

        if (before <= 0 && now > 0) {
            return SignalResult.buy("Golden cross: " + averages);
        }
        if (before >= 0 && now < 0) {
            return SignalResult.sell("Death cross: " + averages);
        }
        return SignalResult.none("No crossover: " + averages);
    }

    private static String show(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
