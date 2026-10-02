package com.team5.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.enums.OrderStatus;
import com.team5.domain.enums.TransactionType;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.CashMovementConflictException;
import com.team5.domain.exception.WithdrawalExceedsAvailableException;
import com.team5.dto.request.CashMovementRequest;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.CashTransactionResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    // retry once if someone else updated the account at the same time
    private static final int MAX_BALANCE_UPDATE_ATTEMPTS = 2;

    private final AccountQueryMapper accountQueryMapper;
    private final TransactionMapper transactionMapper;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public AccountServiceImpl(
        AccountQueryMapper accountQueryMapper,
        TransactionMapper transactionMapper,
        AccountAccessAuthorizer accountAccessAuthorizer
    ) {
        this.accountQueryMapper = accountQueryMapper;
        this.transactionMapper = transactionMapper;
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
    @Transactional
    public CashTransactionResponse deposit(long authenticatedAccountId, Long accountId, CashMovementRequest request) {
        return moveCash(authenticatedAccountId, accountId, request, TransactionType.DEPOSIT);
    }

    @Override
    @Transactional
    public CashTransactionResponse withdraw(long authenticatedAccountId, Long accountId, CashMovementRequest request) {
        return moveCash(authenticatedAccountId, accountId, request, TransactionType.WITHDRAWAL);
    }

    @Override
    public List<CashTransactionResponse> getCashTransactions(long authenticatedAccountId, Long accountId, int limit) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        return transactionMapper.selectCashTransactions(accountId, limit);
    }

    // updates the balance and saves the transactions row together,
    // a duplicate idempotency key rolls back the balance update too
    private CashTransactionResponse moveCash(
        long authenticatedAccountId,
        Long accountId,
        CashMovementRequest request,
        TransactionType type
    ) {
        assertAccountIsReadable(authenticatedAccountId, accountId);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        for (int attempt = 1; attempt <= MAX_BALANCE_UPDATE_ATTEMPTS; attempt++) {
            Map<String, Object> account = accountQueryMapper.findAccountForOrder(accountId);
            if (account == null) {
                throw new AccountNotFoundException();
            }
            BigDecimal balanceBefore = asBigDecimal(account, "balance").setScale(2, RoundingMode.HALF_UP);

            if (type == TransactionType.WITHDRAWAL) {
                BigDecimal held = accountQueryMapper.sumHeldForNewBuyOrders(accountId);
                BigDecimal available = balanceBefore.subtract(held == null ? BigDecimal.ZERO : held).max(BigDecimal.ZERO);
                if (amount.compareTo(available) > 0) {
                    throw new WithdrawalExceedsAvailableException();
                }
            }

            BigDecimal balanceAfter = type == TransactionType.DEPOSIT
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);
            OffsetDateTime now = OffsetDateTime.now();

            int updatedRows = accountQueryMapper.updateBalanceWithVersion(
                accountId, balanceAfter, asLong(account, "version"), now);
            if (updatedRows != 1) {
                continue;
            }

            Long transactionId = toPositiveLong(UUID.randomUUID());
            String currency = asString(account, "base_currency");
            try {
                transactionMapper.insertTransaction(
                    transactionId,
                    accountId,
                    null,
                    null,
                    type.name(),
                    null,
                    null,
                    amount,
                    currency,
                    balanceBefore,
                    balanceAfter,
                    request.getIdempotencyKey(),
                    now
                );
            } catch (DataAccessException exception) {
                throw new CashMovementConflictException("Duplicate cash movement");
            }

            return new CashTransactionResponse(
                transactionId, accountId, type, amount, currency, balanceBefore, balanceAfter, now);
        }

        throw new CashMovementConflictException("Balance changed during the request");
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

    private Long toPositiveLong(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return mixed == Long.MIN_VALUE ? 0L : Math.abs(mixed);
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private BigDecimal asBigDecimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private Long asLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }
}
