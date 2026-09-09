package com.team5.service;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountServicePlaceholder implements AccountService {

    @Override
    public AccountResponse getAccount(Long accountId) {
        return new AccountResponse(
            accountId,
            "ACC-000001",
            "Priya Menon",
            BigDecimal.valueOf(24500.75),
            AccountStatus.ACTIVE,
            7,
            OffsetDateTime.parse("2026-09-28T09:14:22Z")
        );
    }

    @Override
    public BalanceResponse getBalance(Long accountId) {
        return new BalanceResponse(
            accountId,
            BigDecimal.valueOf(24500.75),
            "USD",
            OffsetDateTime.parse("2026-09-28T09:14:22Z")
        );
    }

    @Override
    public List<PositionResponse> getPositions(Long accountId) {
        return List.of(
            new PositionResponse(accountId, "ACME", 100, BigDecimal.valueOf(25.50)),
            new PositionResponse(accountId, "INFY.NS", 40, BigDecimal.valueOf(1580.25))
        );
    }

    @Override
    public List<OrderHistoryEntry> getOrders(Long accountId, OrderStatus status, OffsetDateTime from, OffsetDateTime to) {
        List<OrderHistoryEntry> entries = List.of(
            new OrderHistoryEntry(
                "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
                accountId,
                "ACME",
                OrderSide.BUY,
                100,
                BigDecimal.valueOf(25.50),
                BigDecimal.valueOf(25.48),
                OrderStatus.FILLED,
                "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
                OffsetDateTime.parse("2026-09-28T09:14:22Z")
            )
        );

        if (status == null) {
            return entries;
        }

        return entries.stream()
            .filter(entry -> entry.status() == status)
            .toList();
    }
}
