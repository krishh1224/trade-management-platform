package com.team5.strategies.indicators;

import static com.team5.strategies.indicators.MovingAverageCrossoverTest.prices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.domain.enums.OrderSide;
import com.team5.strategies.domain.SignalResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// A 3-price window, bands 1 standard deviation wide. Before 7 or 14 below: the window 11, 10, 11 has
// mean 10.667 and deviation 0.471, so its bands run from about 10.195 to 11.138.
class BollingerBandsTest {

    private static final BigDecimal ONE_SD = BigDecimal.ONE;

    @Test
    void priceCrossingBelowTheLowerBand_firesBuy() {
        SignalResult signal = BollingerBands.evaluate(prices(10, 11, 10, 11, 7), 3, ONE_SD);

        assertEquals(OrderSide.BUY, signal.side());
        assertTrue(signal.reason().startsWith("Crossed below the lower band"), signal.reason());
    }

    @Test
    void priceCrossingAboveTheUpperBand_firesSell() {
        // 10 was inside its band (up to 11.138); 14 is above the band of 10, 11, 10 (up to 10.805)
        SignalResult signal = BollingerBands.evaluate(prices(11, 10, 11, 10, 14), 3, ONE_SD);

        assertEquals(OrderSide.SELL, signal.side());
        assertTrue(signal.reason().startsWith("Crossed above the upper band"), signal.reason());
    }

    @Test
    void priceAlreadyAboveTheBand_doesNotFireAgain() {
        // 11 was already above the band of 10, 11, 10 (up to 10.805): 14 is no new crossing
        assertFalse(BollingerBands.evaluate(prices(10, 11, 10, 11, 14), 3, ONE_SD).fired());
    }

    @Test
    void priceInsideTheBands_firesNothing() {
        SignalResult signal = BollingerBands.evaluate(prices(10, 11, 10, 11, 10.5), 3, ONE_SD);

        assertFalse(signal.fired());
    }

    @Test
    void priceStayingBelowTheBand_doesNotFireAgain() {
        // 7 crossed; 6 is still below, measured from a previous price that was already outside
        assertFalse(BollingerBands.evaluate(prices(10, 11, 10, 11, 7, 6), 3, ONE_SD).fired());
    }

    @Test
    void widerBands_needABiggerMove() {
        // 10.0 is below the 1-deviation band (10.195) but inside the 2-deviation one (9.724)
        assertEquals(OrderSide.BUY, BollingerBands.evaluate(prices(10, 11, 10, 11, 10), 3, ONE_SD).side());
        assertFalse(BollingerBands.evaluate(prices(10, 11, 10, 11, 10), 3, new BigDecimal("2")).fired());
    }

    @Test
    void flatPrices_anyDropIsBelowTheBand() {
        // no deviation: both bands sit on the mean
        assertEquals(OrderSide.BUY, BollingerBands.evaluate(prices(10, 10, 10, 10, 9.99), 3, ONE_SD).side());
    }

    @Test
    void tooFewPrices_waitsAndFiresNothing() {
        SignalResult signal = BollingerBands.evaluate(prices(10, 11, 10, 7), 3, ONE_SD);

        assertFalse(signal.fired());
        assertEquals("Waiting for price history: 4 of 5 prices", signal.reason());
    }

    @Test
    void zeroWidth_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> BollingerBands.evaluate(prices(10, 11, 10, 11, 7), 3, BigDecimal.ZERO));
    }
}
