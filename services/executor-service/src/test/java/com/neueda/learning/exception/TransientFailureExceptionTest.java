package com.neueda.learning.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransientFailureExceptionTest {

    @Test
    void twoArgConstructorDefaultsRetriesExhaustedToFalse() {
        TransientFailureException exception =
            new TransientFailureException("DB_CONNECTION_LOST", "Temporary connection loss");

        assertEquals("DB_CONNECTION_LOST", exception.getFailureReason());
        assertEquals("Temporary connection loss", exception.getMessage());
        assertFalse(exception.isRetriesExhausted());
    }

    @Test
    void threeArgConstructorSetsRetriesExhaustedExplicitly() {
        TransientFailureException exception =
            new TransientFailureException("OPTIMISTIC_LOCK", "Version mismatch after max retries", true);

        assertEquals("OPTIMISTIC_LOCK", exception.getFailureReason());
        assertTrue(exception.isRetriesExhausted());
    }

    @Test
    void threeArgConstructorWithCauseDefaultsRetriesExhaustedToFalse() {
        Throwable cause = new RuntimeException("root cause");

        TransientFailureException exception =
            new TransientFailureException("NETWORK_ISSUE", "Temporary network issue", cause);

        assertEquals("NETWORK_ISSUE", exception.getFailureReason());
        assertFalse(exception.isRetriesExhausted());
        assertSame(cause, exception.getCause());
    }

    @Test
    void fourArgConstructorSetsEveryField() {
        Throwable cause = new RuntimeException("root cause");

        TransientFailureException exception =
            new TransientFailureException("BROKER_UNAVAILABLE", "Kafka broker unavailable", true, cause);

        assertEquals("BROKER_UNAVAILABLE", exception.getFailureReason());
        assertEquals("Kafka broker unavailable", exception.getMessage());
        assertTrue(exception.isRetriesExhausted());
        assertSame(cause, exception.getCause());
    }
}
