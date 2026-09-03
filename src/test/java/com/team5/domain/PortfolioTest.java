package com.team5.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioTest {

    @Test
    void shouldCreatePortfolioWithValidValues() {
        Portfolio portfolio = createPortfolio("ACTIVE");

        assertEquals(1L, portfolio.getPortfolioId());
        assertEquals("My Portfolio", portfolio.getName());
        assertTrue(portfolio.isActive());
    }

    @Test
    void shouldRejectBlankPortfolioName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Portfolio(
                        1L,
                        " ",
                        1L,
                        "ACTIVE",
                        null,
                        null
                )
        );
    }

    @Test
    void shouldReturnTrueForActivePortfolio() {
        Portfolio portfolio = createPortfolio("ACTIVE");

        assertTrue(portfolio.isActive());
    }

    @Test
    void shouldReturnFalseForInactivePortfolio() {
        Portfolio portfolio = createPortfolio("CLOSED");

        assertFalse(portfolio.isActive());
    }

    private Portfolio createPortfolio(String status) {
        return new Portfolio(
                1L,
                "My Portfolio",
                1L,
                status,
                null,
                null
        );
    }
}