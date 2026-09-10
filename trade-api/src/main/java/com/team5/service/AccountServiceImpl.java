package com.team5.service;

import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.mapper.AccountQueryMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountQueryMapper accountQueryMapper;

    public AccountServiceImpl(AccountQueryMapper accountQueryMapper) {
        this.accountQueryMapper = accountQueryMapper;
    }

    @Override
    public AccountResponse getAccount(Long accountId) {
        return accountQueryMapper.getAccount(accountId);
    }

    @Override
    public BalanceResponse getBalance(Long accountId) {
        return accountQueryMapper.getBalance(accountId);
    }

    @Override
    public List<PositionResponse> getPositions(Long accountId) {
        return accountQueryMapper.getPositions(accountId);
    }

    @Override
    public List<OrderHistoryEntry> getOrders(Long accountId, OrderStatus status, OffsetDateTime from, OffsetDateTime to) {
        String statusName = status == null ? null : status.name();
        return accountQueryMapper.getOrders(accountId, statusName, from, to);
    }
}
