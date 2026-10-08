package com.team5.strategies.indicators;

import static com.team5.strategies.indicators.MovingAverageCrossoverTest.prices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.team5.domain.enums.OrderSide;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorSeriesTest {

    private static final List<BigDecimal> SERIES = prices(12, 11, 10, 9, 14, 15, 16, 12, 8, 7, 9, 13, 17, 10, 4);

    @Test
    void movingAverage_isNullUntilTheWindowFits_thenTheMeanEndingAtEachPrice() {
        List<BigDecimal> sma = IndicatorSeries.movingAverage(prices(12, 11, 10, 9, 14), 2);

        assertNull(sma.get(0));
        assertEquals(new BigDecimal("11.5000"), sma.get(1));
        assertEquals(new BigDecimal("11.5000"), sma.get(4));
    }

    @Test
    void bollinger_measuresEachPriceAgainstTheWindowBeforeIt() {
        IndicatorSeries.Bands bands = IndicatorSeries.bollinger(prices(10, 11, 10, 11, 7), 3, BigDecimal.ONE);

        assertNull(bands.middle().get(2));
        // index 3: window 10, 11, 10 -> mean 10.3333, deviation 0.4714
        assertEquals(new BigDecimal("10.3333"), bands.middle().get(3));
        assertEquals(new BigDecimal("10.8047"), bands.upper().get(3));
        assertEquals(new BigDecimal("9.8619"), bands.lower().get(3));
    }

    @Test
    void theAveragesOnTheChartCrossExactlyWhereTheRuleFires() {
        List<BigDecimal> shortLine = IndicatorSeries.movingAverage(SERIES, 2);
        List<BigDecimal> longLine = IndicatorSeries.movingAverage(SERIES, 4);

        for (int i = 4; i < SERIES.size(); i++) {
            OrderSide fired = MovingAverageCrossover.evaluate(SERIES.subList(0, i + 1), 2, 4).side();
            int before = shortLine.get(i - 1).compareTo(longLine.get(i - 1));
            int now = shortLine.get(i).compareTo(longLine.get(i));
            OrderSide drawn = before <= 0 && now > 0 ? OrderSide.BUY : before >= 0 && now < 0 ? OrderSide.SELL : null;
            assertEquals(fired, drawn, "at index " + i);
        }
    }

    @Test
    void thePriceLeavesTheDrawnBandsExactlyWhereTheRuleFires() {
        IndicatorSeries.Bands bands = IndicatorSeries.bollinger(SERIES, 3, BigDecimal.ONE);

        for (int i = 4; i < SERIES.size(); i++) {
            OrderSide fired = BollingerBands.evaluate(SERIES.subList(0, i + 1), 3, BigDecimal.ONE).side();
            BigDecimal previous = SERIES.get(i - 1);
            BigDecimal current = SERIES.get(i);
            OrderSide drawn = previous.compareTo(bands.lower().get(i - 1)) >= 0 && current.compareTo(bands.lower().get(i)) < 0
                ? OrderSide.BUY
                : previous.compareTo(bands.upper().get(i - 1)) <= 0 && current.compareTo(bands.upper().get(i)) > 0
                    ? OrderSide.SELL : null;
            assertEquals(fired, drawn, "at index " + i);
        }
    }
}
