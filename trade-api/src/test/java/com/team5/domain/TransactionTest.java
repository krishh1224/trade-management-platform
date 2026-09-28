package com.team5.domain;

import com.team5.domain.enums.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Test
    void gettersReturnConstructorValues() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Transaction transaction = new Transaction(
                1L, 2L, 3L, 4L,
                TransactionType.BUY, 10, new BigDecimal("100"),
                new BigDecimal("1000"), "INR",
                new BigDecimal("5000"), new BigDecimal("4000"),
                "TXN123456", createdAt
        );

        assertEquals(1L, transaction.getTransactionId());
        assertEquals(2L, transaction.getAccountId());
        assertEquals(3L, transaction.getOrderId());
        assertEquals(4L, transaction.getInstrumentId());
        assertEquals(TransactionType.BUY, transaction.getTransactionType());
        assertEquals(10, transaction.getQuantity());
        assertEquals(new BigDecimal("100"), transaction.getPrice());
        assertEquals(new BigDecimal("1000"), transaction.getAmount());
        assertEquals("INR", transaction.getCurrency());
        assertEquals(new BigDecimal("5000"), transaction.getBalanceBefore());
        assertEquals(new BigDecimal("4000"), transaction.getBalanceAfter());
        assertEquals("TXN123456", transaction.getIdempotencyKey());
        assertEquals(createdAt, transaction.getCreatedAt());
    }

    @Test
    void toStringContainsKeyFields() {
        Transaction transaction = createTransaction(TransactionType.DEPOSIT);

        String text = transaction.toString();

        assertTrue(text.contains("transactionId=1"));
        assertTrue(text.contains("type=DEPOSIT"));
        assertTrue(text.contains("currency='INR'"));
    }

    private Transaction createTransaction(TransactionType type) {
        return new Transaction(
                1L,
                1L,
                1L,
                1L,
                type,
                10,
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