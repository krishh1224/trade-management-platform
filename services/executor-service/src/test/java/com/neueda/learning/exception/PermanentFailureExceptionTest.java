package com.neueda.learning.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PermanentFailureExceptionTest {

    @Test
    void twoArgConstructorSetsMessageAndFailureReason() {
        PermanentFailureException exception = new PermanentFailureException("MALFORMED_JSON", "Could not parse message");

        assertEquals("MALFORMED_JSON", exception.getFailureReason());
        assertEquals("Could not parse message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void threeArgConstructorAlsoSetsCause() {
        Throwable cause = new RuntimeException("root cause");

        PermanentFailureException exception =
            new PermanentFailureException("ORDER_NOT_FOUND", "Order 123 not found", cause);

        assertEquals("ORDER_NOT_FOUND", exception.getFailureReason());
        assertEquals("Order 123 not found", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
