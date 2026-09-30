package com.team5.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.domain.exception.OrderConflictException;
import com.team5.domain.exception.WatchlistDuplicateException;
import com.team5.domain.exception.WatchlistItemNotFoundException;
import com.team5.dto.request.AddToWatchlistRequest;
import com.team5.dto.response.WatchlistItemResponse;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.OrderMapper;
import com.team5.mapper.WatchlistMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistServiceImpl implements WatchlistService {

    private final WatchlistMapper watchlistMapper;
    private final AccountQueryMapper accountQueryMapper;
    private final OrderMapper orderMapper;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public WatchlistServiceImpl(
        WatchlistMapper watchlistMapper,
        AccountQueryMapper accountQueryMapper,
        OrderMapper orderMapper,
        AccountAccessAuthorizer accountAccessAuthorizer
    ) {
        this.watchlistMapper = watchlistMapper;
        this.accountQueryMapper = accountQueryMapper;
        this.orderMapper = orderMapper;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @Override
    @Transactional
    public WatchlistItemResponse addToWatchlist(long authenticatedAccountId, AddToWatchlistRequest request) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, request.getAccountId());

        Map<String, Object> account = accountQueryMapper.findAccountForOrder(request.getAccountId());
        if (account == null) {
            throw new AccountNotFoundException();
        }
        if (!"ACTIVE".equals(asString(account, "status"))) {
            throw new AccountNotActiveException();
        }

        Map<String, Object> instrument = orderMapper.findInstrumentByTicker(request.getSymbol());
        if (instrument == null || !Boolean.TRUE.equals(asBoolean(instrument, "tradable"))) {
            throw new InstrumentNotFoundException();
        }

        Long instrumentId = asLong(instrument, "instrument_id");
        String instrumentName = asString(instrument, "name");

        if (watchlistMapper.existsInWatchlist(request.getAccountId(), instrumentId)) {
            throw new WatchlistDuplicateException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long watchlistId = toPositiveLong(UUID.randomUUID());

        try {
            int inserted = watchlistMapper.insertWatchlistItem(watchlistId, request.getAccountId(), instrumentId, now);
            if (inserted != 1) {
                throw new OrderConflictException("Watchlist insert did not affect exactly one row");
            }
        } catch (DataAccessException exception) {
            throw new WatchlistDuplicateException();
        }

        return new WatchlistItemResponse(
            watchlistId,
            instrumentId,
            request.getSymbol(),
            instrumentName,
            now
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId) {
        return watchlistMapper.findWatchlistByAccountId(authenticatedAccountId);
    }

    @Override
    @Transactional
    public void removeFromWatchlist(long authenticatedAccountId, long instrumentId) {
        Long ownerAccountId = watchlistMapper.findAccountIdByInstrumentId(authenticatedAccountId, instrumentId);
        if (ownerAccountId == null) {
            throw new WatchlistItemNotFoundException();
        }
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, ownerAccountId);

        int deleted = watchlistMapper.deleteWatchlistItem(authenticatedAccountId, instrumentId);
        if (deleted != 1) {
            throw new WatchlistItemNotFoundException();
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

    private Long asLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new OrderConflictException("Unexpected numeric mapping for " + key);
    }

    private Boolean asBoolean(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
