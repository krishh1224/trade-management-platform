package com.team5.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioHoldingTest {

    @Test
    void shouldCreateHoldingWithValidValues() {
    PortfolioHolding holding = createHolding("100", "50");

        assertEquals(
                0,
                new BigDecimal("100").compareTo(holding.getQuantity())
        );

        assertEquals(
                0,
                new BigDecimal("50").compareTo(holding.getAverageCost())
        );
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createHolding("-1", "50")
        );
    }

    @Test
    void shouldReturnTrueWhenHoldingHasEnoughQuantity() {
        PortfolioHolding holding = createHolding("100", "50");

        assertTrue(holding.hasEnough(new BigDecimal("100")));
    }

    @Test
    void shouldReturnFalseWhenHoldingDoesNotHaveEnoughQuantity() {
        PortfolioHolding holding = createHolding("100", "50");

        assertFalse(holding.hasEnough(new BigDecimal("101")));
    }

    @Test
    void shouldIncreaseQuantityAfterBuy() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.buy(
                new BigDecimal("50"),
                new BigDecimal("60")
        );

        assertEquals(
                new BigDecimal("150"),
                holding.getQuantity()
        );
    }

    @Test
    void shouldRecalculateAverageCostAfterBuy() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.buy(
                new BigDecimal("100"),
                new BigDecimal("70")
        );

        assertEquals(
                new BigDecimal("60"),
                holding.getAverageCost()
        );
    }

    @Test
    void shouldDecreaseQuantityAfterSell() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.sell(new BigDecimal("40"));

        assertEquals(
                new BigDecimal("60"),
                holding.getQuantity()
        );
    }

    @Test
    void shouldKeepAverageCostUnchangedAfterSell() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.sell(new BigDecimal("40"));

        assertEquals(
                0,
                new BigDecimal("50").compareTo(holding.getAverageCost())
        );
    }

    @Test
    void shouldRejectSellGreaterThanHolding() {
        PortfolioHolding holding = createHolding("100", "50");

        assertThrows(
                IllegalStateException.class,
                () -> holding.sell(new BigDecimal("101"))
        );
    }

    private PortfolioHolding createHolding(
            String quantity,
            String averageCost
    ) {
        return new PortfolioHolding(
                1L,
                1L,
                1L,
                new BigDecimal(quantity),
                new BigDecimal(averageCost),
                null,
                null
        );
    }
}