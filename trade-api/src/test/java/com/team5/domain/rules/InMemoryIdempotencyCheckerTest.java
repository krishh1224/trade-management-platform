package com.team5.domain.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryIdempotencyCheckerTest {

    private final InMemoryIdempotencyChecker checker = new InMemoryIdempotencyChecker();

    @Test
    void unseenKeyIsNotADuplicate() {
        assertFalse(checker.isDuplicate("key-1"));
    }

    @Test
    void recordedKeyIsReportedAsDuplicate() {
        checker.record("key-2");

        assertTrue(checker.isDuplicate("key-2"));
    }

    @Test
    void isDuplicateRejectsNullKey() {
        assertThrows(IllegalArgumentException.class, () -> checker.isDuplicate(null));
    }

    @Test
    void recordRejectsNullKey() {
        assertThrows(IllegalArgumentException.class, () -> checker.record(null));
    }
}
