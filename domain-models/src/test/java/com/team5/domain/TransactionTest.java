package com.team5.domain;

import com.team5.domain.enums.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldIdentifyDepositAsCashTransaction() {
        Transaction transaction =
                createTransaction(TransactionType.DEPOSIT);

        assertTrue(transaction.isCashTransaction());
        assertFalse(transaction.isTradeTransaction());
    }

    @Test
    void shouldIdentifyWithdrawalAsCashTransaction() {
        Transaction transaction =
                createTransaction(TransactionType.WITHDRAWAL);

        assertTrue(transaction.isCashTransaction());
        assertFalse(transaction.isTradeTransaction());
    }

    @Test
    void shouldIdentifyBuyAsTradeTransaction() {
        Transaction transaction =
                createTransaction(TransactionType.BUY);

        assertTrue(transaction.isTradeTransaction());
        assertFalse(transaction.isCashTransaction());
    }

    @Test
    void shouldIdentifySellAsTradeTransaction() {
        Transaction transaction =
                createTransaction(TransactionType.SELL);

        assertTrue(transaction.isTradeTransaction());
        assertFalse(transaction.isCashTransaction());
    }

    @Test
    void shouldRejectNullTransactionType() {
        Transaction transaction =
                createTransaction(null);

        Set<ConstraintViolation<Transaction>> violations =
                validator.validate(transaction);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("transactionType"))
        );
    }

    private Transaction createTransaction(TransactionType type) {
        return new Transaction(
                1L,
                1L,
                1L,
                1L,
                type,
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("1000"),
                "INR",
                new BigDecimal("5000"),
                new BigDecimal("4000"),
                "TXN123456",
                null
        );
    }
}