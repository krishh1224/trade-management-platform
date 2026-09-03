package com.team5.domain;

import com.team5.domain.enums.TransactionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    @NotNull
    private final Long transactionId;
    
    @NotNull
    private final Long accountId;
    
    private final Long orderId;
    
    private final Long instrumentId;
    
    @NotNull
    private final TransactionType transactionType;
    
    private final BigDecimal quantity;
    
    private final BigDecimal price;
    
    @NotNull
    @Positive
    private final BigDecimal amount;
    
    @NotBlank
    private final String currency;
    
    @NotNull
    @PositiveOrZero
    private final BigDecimal balanceBefore;
    
    @NotNull
    @PositiveOrZero
    private final BigDecimal balanceAfter;
    
    @NotBlank
    private final String idempotencyKey;
    
    private final LocalDateTime createdAt;

    public Transaction(Long transactionId, Long accountId, Long orderId, Long instrumentId,
                       TransactionType transactionType, BigDecimal quantity, BigDecimal price,
                       BigDecimal amount, String currency,
                       BigDecimal balanceBefore, BigDecimal balanceAfter,
                       String idempotencyKey, LocalDateTime createdAt) {

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