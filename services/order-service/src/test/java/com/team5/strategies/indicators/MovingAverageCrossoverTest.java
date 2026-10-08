package com.team5.strategies.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.domain.enums.OrderSide;
import com.team5.strategies.domain.SignalResult;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// SMA(2) against SMA(4): five prices give the previous and the current pair of averages.
class MovingAverageCrossoverTest {

    @Test
    void shortAverageCrossingAboveTheLong_firesBuy() {
        // before: SMA2 9.5 < SMA4 10.5; now: SMA2 11.5 > SMA4 11
        SignalResult signal = MovingAverageCrossover.evaluate(prices(12, 11, 10, 9, 14), 2, 4);

        assertTrue(signal.fired());
        assertEquals(OrderSide.BUY, signal.side());
        assertTrue(signal.reason().startsWith("Golden cross"), signal.reason());
    }

    @Test
    void shortAverageCrossingBelowTheLong_firesSell() {
        // before: SMA2 10.5 > SMA4 9.5; now: SMA2 8.5 < SMA4 9
        SignalResult signal = MovingAverageCrossover.evaluate(prices(8, 9, 10, 11, 6), 2, 4);

        assertEquals(OrderSide.SELL, signal.side());
        assertTrue(signal.reason().startsWith("Death cross"), signal.reason());
    }

    @Test
    void shortAverageStayingAboveTheLong_firesNothing() {
        SignalResult signal = MovingAverageCrossover.evaluate(prices(10, 11, 12, 13, 14), 2, 4);

        assertFalse(signal.fired());
        assertTrue(signal.reason().startsWith("No crossover"), signal.reason());
    }

    @Test
    void shortAverageStayingBelowTheLong_firesNothing() {
        assertFalse(MovingAverageCrossover.evaluate(prices(14, 13, 12, 11, 10), 2, 4).fired());
    }

    @Test
    void averagesMeetingWithoutCrossing_firesNothing() {
        // before: SMA2 9.5 < SMA4 10.5; now both 10.5: touching is not crossing
        assertFalse(MovingAverageCrossover.evaluate(prices(12, 11, 10, 9, 12), 2, 4).fired());
    }

    @Test
    void aTrendAfterTheCross_firesOnceNotOnEveryQuote() {
        List<BigDecimal> series = prices(12, 11, 10, 9, 14, 15, 16, 17, 18);
        int buys = 0;
        for (int end = 5; end <= series.size(); end++) {
            if (MovingAverageCrossover.evaluate(series.subList(0, end), 2, 4).side() == OrderSide.BUY) {
                buys++;
            }
        }
        assertEquals(1, buys);
    }

    @Test
    void tooFewPrices_waitsAndFiresNothing() {
        SignalResult signal = MovingAverageCrossover.evaluate(prices(12, 11, 10, 14), 2, 4);

        assertFalse(signal.fired());
        assertEquals("Waiting for price history: 4 of 5 prices", signal.reason());
    }

    @Test
    void onlyTheMostRecentPricesCount() {
        // a long history before the window changes nothing
        SignalResult signal = MovingAverageCrossover.evaluate(prices(500, 1, 500, 1, 12, 11, 10, 9, 14), 2, 4);

        assertEquals(OrderSide.BUY, signal.side());
    }

    @Test
    void shortWindowNotBelowTheLong_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> MovingAverageCrossover.evaluate(prices(1, 2, 3, 4, 5), 4, 4));
    }

    static List<BigDecimal> prices(double... values) {
        return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }
}
