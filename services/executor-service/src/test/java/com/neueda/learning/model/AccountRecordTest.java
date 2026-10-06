package com.neueda.learning.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AccountRecordTest {

    @Test
    void primaryConstructorPopulatesAllGetters() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        AccountRecord record = new AccountRecord(
            101L, "1000000001", "LOW", "INR", new BigDecimal("98000.00"), "ACTIVE", 1L, now, now);

        assertEquals(101L, record.getAccId());
        assertEquals("1000000001", record.getAccountNumber());
        assertEquals("LOW", record.getRiskProfile());
        assertEquals("INR", record.getBaseCurrency());
        assertEquals(new BigDecimal("98000.00"), record.getBalance());
        assertEquals("ACTIVE", record.getStatus());
        assertEquals(1L, record.getVersion());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void legacyClientIdConstructorDelegatesToPrimaryConstructor() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        AccountRecord record = new AccountRecord(
            102L, 999L, "1000000002", "MODERATE", "INR",
            new BigDecimal("156000.00"), "ACTIVE", 1L, now, now);

        assertEquals(102L, record.getAccId());
        assertEquals("1000000002", record.getAccountNumber());
        assertEquals("MODERATE", record.getRiskProfile());
        assertEquals(new BigDecimal("156000.00"), record.getBalance());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        AccountRecord record = new AccountRecord();
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

        record.setAccId(103L);
        record.setAccountNumber("1000000003");
        record.setRiskProfile("ADVENTUROUS");
        record.setBaseCurrency("INR");
        record.setBalance(new BigDecimal("82000.00"));
        record.setStatus("ACTIVE");
        record.setVersion(2L);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        assertEquals(103L, record.getAccId());
        assertEquals("1000000003", record.getAccountNumber());
        assertEquals("ADVENTUROUS", record.getRiskProfile());
        assertEquals("INR", record.getBaseCurrency());
        assertEquals(new BigDecimal("82000.00"), record.getBalance());
        assertEquals("ACTIVE", record.getStatus());
        assertEquals(2L, record.getVersion());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void toStringContainsKeyFields() {
        AccountRecord record = new AccountRecord(
            101L, "1000000001", "LOW", "INR", new BigDecimal("98000.00"), "ACTIVE", 1L, null, null);

        String text = record.toString();

        assertTrue(text.contains("accId=101"));
        assertTrue(text.contains("accountNumber='1000000001'"));
        assertTrue(text.contains("status='ACTIVE'"));
    }
}
