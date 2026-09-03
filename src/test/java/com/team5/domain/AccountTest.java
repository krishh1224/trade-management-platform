package com.team5.domain;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.RiskProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void shouldCreateAccountWithValidValues() {
        Account account = createAccount("1000.00");

        assertEquals("1000.00", account.getBalance().toPlainString());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Account(
                        null, 1L, "ACC001", RiskProfile.LOW,
                        "INR", new BigDecimal("1000.00"),
                        AccountStatus.ACTIVE, null, null
                )
        );
    }

    @Test
    void shouldRejectNegativeOpeningBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createAccount("-1.00")
        );
    }

    @Test
    void shouldReturnTrueWhenAccountCanAffordAmount() {
        Account account = createAccount("1000.00");

        assertTrue(account.canAfford(new BigDecimal("500.00")));
    }

    @Test
    void shouldReturnFalseWhenAccountCannotAffordAmount() {
        Account account = createAccount("1000.00");

        assertFalse(account.canAfford(new BigDecimal("1000.01")));
    }

    @Test
    void shouldDebitBalance() {
        Account account = createAccount("1000.00");

        account.debit(new BigDecimal("250.00"));

        assertEquals("750.00", account.getBalance().toPlainString());
    }

    @Test
    void shouldRejectDebitGreaterThanBalance() {
        Account account = createAccount("1000.00");

        assertThrows(
                IllegalStateException.class,
                () -> account.debit(new BigDecimal("1000.01"))
        );

        assertEquals("1000.00", account.getBalance().toPlainString());
    }

    @Test
    void shouldRejectNonPositiveDebit() {
        Account account = createAccount("1000.00");

        assertThrows(
                IllegalArgumentException.class,
                () -> account.debit(BigDecimal.ZERO)
        );
    }

    @Test
    void shouldCreditBalance() {
        Account account = createAccount("1000.00");

        account.credit(new BigDecimal("250.00"));

        assertEquals("1250.00", account.getBalance().toPlainString());
    }

    @Test
    void shouldSuspendActiveAccount() {
        Account account = createAccount("1000.00");

        account.suspend();

        assertEquals(AccountStatus.SUSPENDED, account.getStatus());
        assertFalse(account.isActive());
    }

    private Account createAccount(String balance) {
        return new Account(
                1L,
                1L,
                "ACC001",
                RiskProfile.LOW,
                "INR",
                new BigDecimal(balance),
                AccountStatus.ACTIVE,
                null,
                null
        );
    }
}