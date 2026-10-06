package com.team5.mapper;

import com.team5.dto.response.CashTransactionResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransactionMapper {

    int insertTransaction(
        @Param("transactionId") Long transactionId,
        @Param("accountId") Long accountId,
        @Param("orderId") Long orderId,
        @Param("instrumentId") Long instrumentId,
        @Param("transactionType") String transactionType,
        @Param("quantity") Long quantity,
        @Param("price") BigDecimal price,
        @Param("amount") BigDecimal amount,
        @Param("currency") String currency,
        @Param("balanceBefore") BigDecimal balanceBefore,
        @Param("balanceAfter") BigDecimal balanceAfter,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("createdAt") OffsetDateTime createdAt
    );

    // only DEPOSIT and WITHDRAWAL rows, latest first
    List<CashTransactionResponse> selectCashTransactions(
        @Param("accountId") Long accountId,
        @Param("limit") int limit
    );
}
