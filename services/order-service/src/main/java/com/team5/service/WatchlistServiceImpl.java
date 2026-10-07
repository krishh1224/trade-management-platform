package com.team5.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.domain.exception.OrderConflictException;
import com.team5.domain.exception.WatchlistDuplicateException;
import com.team5.domain.exception.WatchlistItemNotFoundException;
import com.team5.dto.request.AddToWatchlistRequest;
import com.team5.dto.request.CreateWatchlistGroupRequest;
import com.team5.dto.response.WatchlistGroupResponse;
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

    private static final String DEFAULT_WATCHLIST_NAME = "Favourite";

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
        Long watchlistGroupId = resolveWatchlistGroupId(request.getAccountId(), request.getWatchlistGroupId());
        String watchlistName = resolveWatchlistName(request.getAccountId(), watchlistGroupId);

        if (watchlistMapper.existsInWatchlist(watchlistGroupId, instrumentId)) {
            throw new WatchlistDuplicateException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long watchlistId = toPositiveLong(UUID.randomUUID());

        try {
            int inserted = watchlistMapper.insertWatchlistItem(
                watchlistId,
                request.getAccountId(),
                watchlistGroupId,
                instrumentId,
                now
            );
            if (inserted != 1) {
                throw new OrderConflictException("Watchlist insert did not affect exactly one row");
            }
        } catch (DataAccessException exception) {
            throw new WatchlistDuplicateException();
        }

        return new WatchlistItemResponse(
            watchlistId,
            watchlistGroupId,
            watchlistName,
            instrumentId,
            request.getSymbol(),
            instrumentName,
            now,
            null,
            null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId) {
        return listWatchlist(authenticatedAccountId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId, Long watchlistGroupId) {
        if (watchlistGroupId != null) {
            validateWatchlistGroupOwnership(authenticatedAccountId, watchlistGroupId);
        }
        return watchlistMapper.findWatchlistByAccountId(authenticatedAccountId, watchlistGroupId);
    }

    @Override
    @Transactional
    public List<WatchlistGroupResponse> listWatchlistGroups(long authenticatedAccountId) {
        ensureDefaultGroup(authenticatedAccountId);
        return watchlistMapper.findWatchlistGroups(authenticatedAccountId);
    }

    @Override
    @Transactional
    public WatchlistGroupResponse createWatchlistGroup(long authenticatedAccountId, CreateWatchlistGroupRequest request) {
        String normalizedName = request.getName().trim();
        if (watchlistMapper.findWatchlistGroupByName(authenticatedAccountId, normalizedName) != null) {
            throw new WatchlistDuplicateException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long watchlistGroupId = toPositiveLong(UUID.randomUUID());
        int inserted = watchlistMapper.insertWatchlistGroup(watchlistGroupId, authenticatedAccountId, normalizedName, now);
        if (inserted != 1) {
            throw new OrderConflictException("Watchlist group insert did not affect exactly one row");
        }
        return new WatchlistGroupResponse(watchlistGroupId, normalizedName, now);
    }

    @Override
    @Transactional
    public void removeFromWatchlist(long authenticatedAccountId, long watchlistId) {
        Long ownerAccountId = watchlistMapper.findAccountIdByWatchlistId(authenticatedAccountId, watchlistId);
        if (ownerAccountId == null) {
            throw new WatchlistItemNotFoundException();
        }
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, ownerAccountId);

        int deleted = watchlistMapper.deleteWatchlistItem(authenticatedAccountId, watchlistId);
        if (deleted != 1) {
            throw new WatchlistItemNotFoundException();
        }
    }

    private Long resolveWatchlistGroupId(Long accountId, Long requestedGroupId) {
        if (requestedGroupId != null) {
            validateWatchlistGroupOwnership(accountId, requestedGroupId);
            return requestedGroupId;
        }
        return ensureDefaultGroup(accountId);
    }

    private void validateWatchlistGroupOwnership(Long accountId, Long watchlistGroupId) {
        Long existing = watchlistMapper.findWatchlistGroupById(accountId, watchlistGroupId);
        if (existing == null) {
            throw new WatchlistItemNotFoundException();
        }
    }

    private Long ensureDefaultGroup(Long accountId) {
        Long existingDefault = watchlistMapper.findDefaultWatchlistGroupId(accountId);
        if (existingDefault != null) {
            return existingDefault;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Long groupId = toPositiveLong(UUID.randomUUID());
        int inserted = watchlistMapper.insertWatchlistGroup(groupId, accountId, DEFAULT_WATCHLIST_NAME, now);
        if (inserted != 1) {
            throw new OrderConflictException("Default watchlist group insert failed");
        }
        return groupId;
    }

    private String resolveWatchlistName(Long accountId, Long watchlistGroupId) {
        return watchlistMapper.findWatchlistGroups(accountId)
            .stream()
            .filter(group -> watchlistGroupId.equals(group.watchlistGroupId()))
            .map(WatchlistGroupResponse::name)
            .findFirst()
            .orElse(DEFAULT_WATCHLIST_NAME);
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
