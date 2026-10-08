package com.team5.strategies.indicators;

import com.team5.strategies.domain.SignalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Bollinger-band breakout over the last prices consumed for a symbol, oldest first. The band a price is
 * measured against is the mean of the {@code window} prices before it, plus or minus {@code width}
 * population standard deviations of those prices.
 *
 * <pre>
 *   BUY:  the previous price was at or above its lower band, and the current price is below its lower band
 *   SELL: the previous price was at or below its upper band, and the current price is above its upper band
 * </pre>
 *
 * The band excludes the price being tested. Including it would cap how far outside the band a price can
 * ever be (a single value is at most sqrt(window - 1) deviations from a mean it is part of), so a narrow
 * window could never fire. It is a crossing: a price that stays outside the band does not fire again.
 * It needs {@code window + 2} prices: a full window before each of the previous and current prices.
 */
public final class BollingerBands {

    private BollingerBands() {
    }

    public static int requiredPrices(int window) {
        return window + 2;
    }

    public static SignalResult evaluate(List<BigDecimal> prices, int window, BigDecimal width) {
        if (window < 2 || width == null || width.signum() <= 0) {
            throw new IllegalArgumentException("need window >= 2 and width > 0, got " + window + "/" + width);
        }
        int required = requiredPrices(window);
        if (prices.size() < required) {
            return SignalResult.none("Waiting for price history: " + prices.size() + " of " + required + " prices");
        }

        int n = prices.size();
        BigDecimal previousPrice = prices.get(n - 2);
        BigDecimal currentPrice = prices.get(n - 1);
        Band previous = band(prices, n - 2, window, width);
        Band current = band(prices, n - 1, window, width);

        String where = "price " + show(currentPrice) + ", bands " + show(current.lower) + " to " + show(current.upper);
        if (previousPrice.compareTo(previous.lower) >= 0 && currentPrice.compareTo(current.lower) < 0) {
            return SignalResult.buy("Crossed below the lower band: " + where);
        }
        if (previousPrice.compareTo(previous.upper) <= 0 && currentPrice.compareTo(current.upper) > 0) {
            return SignalResult.sell("Crossed above the upper band: " + where);
        }
        return SignalResult.none("Inside the bands or already outside: " + where);
    }

    // the band for the price at index `at`, from the window of prices before it
    private static Band band(List<BigDecimal> prices, int at, int window, BigDecimal width) {
        BigDecimal mean = Indicators.mean(prices, at, window);
        BigDecimal spread = Indicators.standardDeviation(prices, at, window).multiply(width, Indicators.MC);
        return new Band(mean.subtract(spread, Indicators.MC), mean.add(spread, Indicators.MC));
    }

    private static String show(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private record Band(BigDecimal lower, BigDecimal upper) {
    }
}
