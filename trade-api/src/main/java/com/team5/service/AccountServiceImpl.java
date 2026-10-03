package com.team5.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.enums.OrderStatus;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.InstrumentOptionResponse;
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
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public AccountServiceImpl(AccountQueryMapper accountQueryMapper, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.accountQueryMapper = accountQueryMapper;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @Override
    public AccountResponse getAccount(long authenticatedAccountId, Long accountId) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        return accountQueryMapper.getAccount(accountId);
    }

    @Override
    public BalanceResponse getBalance(long authenticatedAccountId, Long accountId) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        return accountQueryMapper.getBalance(accountId);
    }

    @Override
    public List<PositionResponse> getPositions(long authenticatedAccountId, Long accountId) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        return accountQueryMapper.getPositions(accountId);
    }

    @Override
    public List<OrderHistoryEntry> getOrders(
        long authenticatedAccountId,
        Long accountId,
        OrderStatus status,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        String statusName = status == null ? null : status.name();
        return accountQueryMapper.getOrders(accountId, statusName, from, to);
    }

    @Override
    public List<InstrumentOptionResponse> getInstruments(long authenticatedAccountId, Long accountId, String prefix) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        return accountQueryMapper.getInstruments(prefix);
    }

    private void assertAccountIsReadable(long authenticatedAccountId, Long accountId) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
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
