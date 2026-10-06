package com.team5.dto.response;

import com.team5.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

// one deposit or withdrawal row from the transactions table
public record CashTransactionResponse(
    Long transactionId,
    Long accountId,
    TransactionType type,
    BigDecimal amount,
    String currency,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    OffsetDateTime createdAt
) {
}
