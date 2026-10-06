package com.team5.domain;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.RiskProfile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateAccountWithValidValues() {
        Account account = createAccount("1000.00");

        assertEquals("1000.00", account.getBalance().toPlainString());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());

        Set<ConstraintViolation<Account>> violations = validator.validate(account);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldRejectNullAccountId() {
        Account account = new Account(
                null,
                "ACC0000001",
                RiskProfile.LOW,
                "INR",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                null,
                null
        );

        Set<ConstraintViolation<Account>> violations = validator.validate(account);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("accId"))
        );
    }

    @Test
    void shouldRejectNegativeOpeningBalance() {
        Account account = createAccount("-1.00");

        Set<ConstraintViolation<Account>> violations = validator.validate(account);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("balance"))
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

    @Test
    void shouldNotDriftMoneyAfterRepeatedOperations() {
        Account account = createAccount("1000.00");

        for (int i = 0; i < 100; i++) {
            account.debit(new BigDecimal("0.10"));
            account.credit(new BigDecimal("0.10"));
        }

        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(account.getBalance())
        );
    }

    @Test
    void gettersReturnConstructorValues() {
        Account account = createAccount("1000.00");

        assertEquals(1L, account.getAccId());
        assertEquals("ACC0000001", account.getAccountNumber());
        assertEquals(RiskProfile.LOW, account.getRiskProfile());
        assertEquals("INR", account.getBaseCurrency());
        assertNull(account.getCreatedAt());
        assertNull(account.getUpdatedAt());
    }

    @Test
    void isActiveReturnsTrueForActiveAccount() {
        Account account = createAccount("1000.00");

        assertTrue(account.isActive());
    }

    @Test
    void toStringContainsKeyFields() {
        Account account = createAccount("1000.00");

        String text = account.toString();

        assertTrue(text.contains("accId=1"));
        assertTrue(text.contains("accountNumber='ACC0000001'"));
        assertTrue(text.contains("status=ACTIVE"));
    }

    private Account createAccount(String balance) {
        return new Account(
                1L,
                "ACC0000001",
                RiskProfile.LOW,
                "INR",
                new BigDecimal(balance),
                AccountStatus.ACTIVE,
                null,
                null
        );
    }
}