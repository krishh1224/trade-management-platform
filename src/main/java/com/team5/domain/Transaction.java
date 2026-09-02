package com.team5.domain;

import com.team5.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private final Long transactionId;
    private final Long accountId;
    private final Long orderId;
    private final Long instrumentId;
    private final TransactionType transactionType;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal amount;
    private final String currency;
    private final BigDecimal balanceBefore;
    private final BigDecimal balanceAfter;
    private final String idempotencyKey;
    private final LocalDateTime createdAt;

    public Transaction(Long transactionId, Long accountId, Long orderId, Long instrumentId,
                       TransactionType transactionType, BigDecimal quantity, BigDecimal price,
                       BigDecimal amount, String currency,
                       BigDecimal balanceBefore, BigDecimal balanceAfter,
                       String idempotencyKey, LocalDateTime createdAt) {

        if (transactionId == null)          throw new IllegalArgumentException("transactionId must not be null.");
        if (accountId == null)              throw new IllegalArgumentException("accountId must not be null.");
        if (transactionType == null)        throw new IllegalArgumentException("transactionType must not be null.");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be positive.");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank.");
        if (balanceBefore == null || balanceBefore.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("balanceBefore must not be negative.");
        if (balanceAfter == null || balanceAfter.compareTo(BigDecimal.ZERO) < 0)   throw new IllegalArgumentException("balanceAfter must not be negative.");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank.");

        this.transactionId   = transactionId;
        this.accountId       = accountId;
        this.orderId         = orderId;
        this.instrumentId    = instrumentId;
        this.transactionType = transactionType;
        this.quantity        = quantity;
        this.price           = price;
        this.amount          = amount;
        this.currency        = currency;
        this.balanceBefore   = balanceBefore;
        this.balanceAfter    = balanceAfter;
        this.idempotencyKey  = idempotencyKey;
        this.createdAt       = createdAt;
    }

    public boolean isCashTransaction() {
        return transactionType == TransactionType.DEPOSIT
            || transactionType == TransactionType.WITHDRAWAL;
    }

    public boolean isTradeTransaction() {
        return transactionType == TransactionType.BUY
            || transactionType == TransactionType.SELL;
    }

    public Long getTransactionId()         { return transactionId; }
    public Long getAccountId()             { return accountId; }
    public Long getOrderId()               { return orderId; }
    public Long getInstrumentId()          { return instrumentId; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getQuantity()        { return quantity; }
    public BigDecimal getPrice()           { return price; }
    public BigDecimal getAmount()          { return amount; }
    public String getCurrency()            { return currency; }
    public BigDecimal getBalanceBefore()   { return balanceBefore; }
    public BigDecimal getBalanceAfter()    { return balanceAfter; }
    public String getIdempotencyKey()      { return idempotencyKey; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    @Override
    public String toString() {
        return "Transaction{transactionId=" + transactionId
            + ", accountId=" + accountId + ", type=" + transactionType
            + ", amount=" + amount + ", currency='" + currency + "'}";
    }
}