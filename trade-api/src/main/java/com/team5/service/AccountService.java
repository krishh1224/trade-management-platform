package com.team5.service;

import com.team5.domain.enums.OrderStatus;
import com.team5.dto.request.CashMovementRequest;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.CashTransactionResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import java.time.OffsetDateTime;
import java.util.List;

public interface AccountService {

    AccountResponse getAccount(long authenticatedAccountId, Long accountId);

    BalanceResponse getBalance(long authenticatedAccountId, Long accountId);

    List<PositionResponse> getPositions(long authenticatedAccountId, Long accountId);

    List<OrderHistoryEntry> getOrders(
        long authenticatedAccountId,
        Long accountId,
        OrderStatus status,
        OffsetDateTime from,
        OffsetDateTime to
    );

    CashTransactionResponse deposit(long authenticatedAccountId, Long accountId, CashMovementRequest request);

    CashTransactionResponse withdraw(long authenticatedAccountId, Long accountId, CashMovementRequest request);

    List<CashTransactionResponse> getCashTransactions(long authenticatedAccountId, Long accountId, int limit);
}
