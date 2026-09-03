package com.team5.domain;

import com.team5.domain.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

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
        assertThrows(
                IllegalArgumentException.class,
                () -> createTransaction(null)
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