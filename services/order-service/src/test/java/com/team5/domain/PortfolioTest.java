package com.team5.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreatePortfolioWithValidValues() {
        Portfolio portfolio = createPortfolio("ACTIVE");

        assertEquals(1L, portfolio.getPortfolioId());
        assertEquals("My Portfolio", portfolio.getName());
        assertTrue(portfolio.isActive());

        assertTrue(validator.validate(portfolio).isEmpty());
    }

    @Test
    void shouldRejectBlankPortfolioName() {
        Portfolio portfolio = new Portfolio(
                1L,
                " ",
                1L,
                "ACTIVE",
                null,
                null
        );

        Set<ConstraintViolation<Portfolio>> violations =
                validator.validate(portfolio);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("name"))
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

    @Test
    void gettersReturnConstructorValues() {
        Portfolio portfolio = createPortfolio("ACTIVE");

        assertEquals(1L, portfolio.getPortfolioId());
        assertEquals("My Portfolio", portfolio.getName());
        assertEquals(1L, portfolio.getAccountId());
        assertEquals("ACTIVE", portfolio.getStatus());
        assertNull(portfolio.getCreatedAt());
        assertNull(portfolio.getUpdatedAt());
    }

    @Test
    void toStringContainsKeyFields() {
        Portfolio portfolio = createPortfolio("ACTIVE");

        String text = portfolio.toString();

        assertTrue(text.contains("portfolioId=1"));
        assertTrue(text.contains("name='My Portfolio'"));
        assertTrue(text.contains("status='ACTIVE'"));
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