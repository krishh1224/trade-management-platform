package com.team5.watchlists.domain;

import static com.team5.notifications.api.AlertDirection.ABOVE;
import static com.team5.notifications.api.AlertDirection.BELOW;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team5.notifications.api.AlertDirection;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CrossingRuleTest {

    @ParameterizedTest(name = "{0} {1}: {2} -> {3} crossed={4}")
    @CsvSource({
        // ABOVE: previous < threshold AND current >= threshold
        "ABOVE, 100, 95,  99.99, false",   // rising but short of the threshold
        "ABOVE, 100, 95,  100,   true",    // reaches it exactly
        "ABOVE, 100, 95,  105,   true",    // goes through it
        "ABOVE, 100, 105, 110,   false",   // already above: staying beyond is not a crossing
        "ABOVE, 100, 100, 105,   false",   // sat on the threshold, so it was not below it
        "ABOVE, 100, 105, 95,    false",   // falling through an ABOVE threshold
        // BELOW: previous > threshold AND current <= threshold
        "BELOW, 100, 105, 100.01, false",  // falling but short of the threshold
        "BELOW, 100, 105, 100,   true",    // reaches it exactly
        "BELOW, 100, 105, 90,    true",    // goes through it
        "BELOW, 100, 95,  90,    false",   // already below: staying beyond is not a crossing
        "BELOW, 100, 100, 95,    false",   // sat on the threshold, so it was not above it
        "BELOW, 100, 95,  105,   false",   // rising through a BELOW threshold
    })
    void crossingIsAMoveThroughTheThreshold(AlertDirection direction, String threshold, String previous,
                                            String current, boolean expected) {
        assertEquals(expected, CrossingRule.crossed(direction, new BigDecimal(threshold),
            new BigDecimal(previous), new BigDecimal(current)));
    }

    @ParameterizedTest
    @CsvSource({"ABOVE", "BELOW"})
    void withoutAPreviousPriceNothingIsCrossed(AlertDirection direction) {
        assertEquals(false, CrossingRule.crossed(direction, BigDecimal.TEN, null, BigDecimal.ONE));
        assertEquals(false, CrossingRule.crossed(direction, BigDecimal.ONE, null, BigDecimal.TEN));
    }

    @ParameterizedTest
    @CsvSource({"100.0000, 100", "100, 100.0000"})
    void scaleDoesNotMatter(String threshold, String current) {
        assertEquals(true, CrossingRule.crossed(ABOVE, new BigDecimal(threshold), new BigDecimal("99"), new BigDecimal(current)));
        assertEquals(true, CrossingRule.crossed(BELOW, new BigDecimal(threshold), new BigDecimal("101"), new BigDecimal(current)));
    }
}
