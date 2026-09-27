package com.neueda.learning.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class QuoteUnavailableExceptionTest {

    @Test
    void messageOnlyConstructorHasNoCause() {
        QuoteUnavailableException exception = new QuoteUnavailableException("Quote unavailable for INFY.NS");

        assertEquals("Quote unavailable for INFY.NS", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void messageAndCauseConstructorSetsBoth() {
        Throwable cause = new RuntimeException("Connection timeout");

        QuoteUnavailableException exception =
            new QuoteUnavailableException("Quote unavailable for INFY.NS after retries", cause);

        assertEquals("Quote unavailable for INFY.NS after retries", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
