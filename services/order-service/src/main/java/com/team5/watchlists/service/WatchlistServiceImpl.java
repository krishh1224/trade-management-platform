package com.team5.watchlists.service;

import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.domain.exception.OrderConflictException;
import com.team5.service.AccountService;
import com.team5.watchlists.domain.WatchlistEntry;
import com.team5.watchlists.domain.WatchlistGroupRef;
import com.team5.watchlists.domain.WatchlistInstrument;
import com.team5.watchlists.dto.AddToWatchlistRequest;
import com.team5.watchlists.dto.CreateWatchlistGroupRequest;
import com.team5.watchlists.dto.WatchlistGroupResponse;
import com.team5.watchlists.dto.WatchlistItemResponse;
import com.team5.watchlists.exception.FavouriteWatchlistNotDeletableException;
import com.team5.watchlists.exception.WatchlistDuplicateException;
import com.team5.watchlists.exception.WatchlistItemNotFoundException;
import com.team5.watchlists.mapper.WatchlistMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistServiceImpl implements WatchlistService {

    private static final String DEFAULT_WATCHLIST_NAME = "Favourite";

    private final WatchlistMapper watchlistMapper;
    // the accounts module's Java interface: existence, ownership and ACTIVE status of an account
    private final AccountService accountService;
    private final WatchlistAccessGuard accessGuard;

    public WatchlistServiceImpl(
        WatchlistMapper watchlistMapper,
        AccountService accountService,
        WatchlistAccessGuard accessGuard
    ) {
        this.watchlistMapper = watchlistMapper;
        this.accountService = accountService;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional
    public WatchlistItemResponse addToWatchlist(long authenticatedAccountId, AddToWatchlistRequest request) {
        accessGuard.assertSameAccount(authenticatedAccountId, request.getAccountId());
        // ACC-404 when the account does not exist, ACC-403 when it is not ACTIVE
        accountService.getAccount(authenticatedAccountId, request.getAccountId());

        WatchlistInstrument instrument = watchlistMapper.findInstrumentByTicker(request.getSymbol());
        if (instrument == null || !Boolean.TRUE.equals(instrument.tradable())) {
            throw new InstrumentNotFoundException();
        }

        Long instrumentId = instrument.instrumentId();
        String instrumentName = instrument.name();
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
            null,
            instrument.currency()
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
        WatchlistEntry entry = watchlistMapper.findWatchlistEntry(watchlistId);
        if (entry == null) {
            throw new WatchlistItemNotFoundException();
        }
        // another account's entry is ACC-403, and nothing is deleted
        accessGuard.assertSameAccount(authenticatedAccountId, entry.accountId());

        int deleted = watchlistMapper.deleteWatchlistItem(authenticatedAccountId, watchlistId);
        if (deleted != 1) {
            throw new WatchlistItemNotFoundException();
        }
    }

    @Override
    @Transactional
    public void deleteWatchlistGroup(long authenticatedAccountId, long watchlistGroupId) {
        WatchlistGroupRef group = watchlistMapper.findWatchlistGroup(watchlistGroupId);
        if (group == null) {
            throw new WatchlistItemNotFoundException();
        }
        // another account's watchlist is ACC-403, and nothing is deleted
        accessGuard.assertSameAccount(authenticatedAccountId, group.accountId());
        // the default every add without a group goes to; recreated on demand, so it is simply kept
        if (DEFAULT_WATCHLIST_NAME.equalsIgnoreCase(group.name())) {
            throw new FavouriteWatchlistNotDeletableException();
        }

        if (watchlistMapper.deleteWatchlistGroup(authenticatedAccountId, watchlistGroupId) != 1) {
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
        Long ownerAccountId = watchlistMapper.findWatchlistGroupOwner(watchlistGroupId);
        if (ownerAccountId == null) {
            throw new WatchlistItemNotFoundException();
        }
        accessGuard.assertSameAccount(accountId, ownerAccountId);
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
}
