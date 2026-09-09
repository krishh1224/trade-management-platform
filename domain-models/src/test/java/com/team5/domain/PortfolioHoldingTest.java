package com.team5.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioHoldingTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateHoldingWithValidValues() {
        PortfolioHolding holding = createHolding("100", "50");

        assertEquals(100, holding.getQuantity());

        assertEquals(
                0,
                new BigDecimal("50").compareTo(holding.getAverageCost())
        );

        assertTrue(validator.validate(holding).isEmpty());
    }

    @Test
    void shouldRejectNegativeQuantity() {
        PortfolioHolding holding = createHolding("-1", "50");

        Set<ConstraintViolation<PortfolioHolding>> violations =
                validator.validate(holding);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"))
        );
    }

    @Test
    void shouldReturnTrueWhenHoldingHasEnoughQuantity() {
        PortfolioHolding holding = createHolding("100", "50");

        assertTrue(holding.hasEnough(100));
    }

    @Test
    void shouldReturnFalseWhenHoldingDoesNotHaveEnoughQuantity() {
        PortfolioHolding holding = createHolding("100", "50");

        assertFalse(holding.hasEnough(101));
    }

    @Test
    void shouldIncreaseQuantityAfterBuy() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.buy(
                50,
                new BigDecimal("60")
        );

        assertEquals(150, holding.getQuantity());
    }

    @Test
    void shouldRecalculateAverageCostAfterBuy() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.buy(
                100,
                new BigDecimal("70")
        );

        assertEquals(
                0,
                new BigDecimal("60").compareTo(holding.getAverageCost())
        );
    }

    @Test
    void shouldDecreaseQuantityAfterSell() {
        PortfolioHolding holding = createHolding("100", "50");

        holding.sell(40);

        assertEquals(60, holding.getQuantity());
    }


    @Test
    void shouldRejectSellGreaterThanHolding() {
        PortfolioHolding holding = createHolding("100", "50");

        assertThrows(
                IllegalStateException.class,
                () -> holding.sell(101)
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
                Integer.parseInt(quantity),
                new BigDecimal(averageCost),
                null,
                null
        );
    }
}