package com.team5.strategies.indicators;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

// Moving-window statistics over a price series held oldest first. Each takes the window of `window`
// prices that ends just before index `endExclusive`.
final class Indicators {

    static final MathContext MC = MathContext.DECIMAL64;

    private Indicators() {
    }

    static BigDecimal mean(List<BigDecimal> prices, int endExclusive, int window) {
        checkRange(prices, endExclusive, window);
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endExclusive - window; i < endExclusive; i++) {
            sum = sum.add(prices.get(i), MC);
        }
        return sum.divide(BigDecimal.valueOf(window), MC);
    }

    // population standard deviation, as Bollinger bands are conventionally drawn
    static BigDecimal standardDeviation(List<BigDecimal> prices, int endExclusive, int window) {
        BigDecimal mean = mean(prices, endExclusive, window);
        BigDecimal squares = BigDecimal.ZERO;
        for (int i = endExclusive - window; i < endExclusive; i++) {
            BigDecimal deviation = prices.get(i).subtract(mean, MC);
            squares = squares.add(deviation.multiply(deviation, MC), MC);
        }
        return squares.divide(BigDecimal.valueOf(window), MC).sqrt(MC);
    }

    private static void checkRange(List<BigDecimal> prices, int endExclusive, int window) {
        if (window < 1 || endExclusive > prices.size() || endExclusive - window < 0) {
            throw new IllegalArgumentException("window " + window + " ending at " + endExclusive
                + " does not fit " + prices.size() + " prices");
        }
    }
}
