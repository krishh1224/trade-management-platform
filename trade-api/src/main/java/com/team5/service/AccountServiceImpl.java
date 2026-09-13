package com.team5.service;

import com.team5.domain.enums.OrderStatus;
import com.team5.exception.AccountNotActiveException;
import com.team5.exception.AccountNotFoundException;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.mapper.AccountQueryMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountQueryMapper accountQueryMapper;

    public AccountServiceImpl(AccountQueryMapper accountQueryMapper) {
        this.accountQueryMapper = accountQueryMapper;
    }

    @Override
    public AccountResponse getAccount(Long accountId) {
        assertAccountIsReadable(accountId);
        return accountQueryMapper.getAccount(accountId);
    }

    @Override
    public BalanceResponse getBalance(Long accountId) {
        assertAccountIsReadable(accountId);
        return accountQueryMapper.getBalance(accountId);
    }

    @Override
    public List<PositionResponse> getPositions(Long accountId) {
        assertAccountIsReadable(accountId);
        return accountQueryMapper.getPositions(accountId);
    }

    @Override
    public List<OrderHistoryEntry> getOrders(Long accountId, OrderStatus status, OffsetDateTime from, OffsetDateTime to) {
        assertAccountIsReadable(accountId);
        String statusName = status == null ? null : status.name();
        return accountQueryMapper.getOrders(accountId, statusName, from, to);
    }

    private void assertAccountIsReadable(Long accountId) {
        Map<String, Object> account = accountQueryMapper.findAccountForOrder(accountId);
        if (account == null) {
            throw new AccountNotFoundException();
        }

        String status = asString(account, "status");
        if (!"ACTIVE".equals(status)) {
            throw new AccountNotActiveException();
        }
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
