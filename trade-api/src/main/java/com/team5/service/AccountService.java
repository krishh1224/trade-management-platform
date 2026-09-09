package com.team5.service;

import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import java.time.OffsetDateTime;
import java.util.List;

public interface AccountService {

    AccountResponse getAccount(Long accountId);

    BalanceResponse getBalance(Long accountId);

    List<PositionResponse> getPositions(Long accountId);

    List<OrderHistoryEntry> getOrders(Long accountId, OrderStatus status, OffsetDateTime from, OffsetDateTime to);
}
