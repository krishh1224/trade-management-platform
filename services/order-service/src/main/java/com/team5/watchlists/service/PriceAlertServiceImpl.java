package com.team5.watchlists.service;

import com.team5.domain.exception.OrderConflictException;
import com.team5.notifications.api.AlertDirection;
import com.team5.service.AccountService;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.PriceAlertStatus;
import com.team5.watchlists.domain.WatchlistEntry;
import com.team5.watchlists.dto.CreatePriceAlertRequest;
import com.team5.watchlists.dto.PriceAlertListResponse;
import com.team5.watchlists.dto.PriceAlertResponse;
import com.team5.watchlists.exception.DuplicatePriceAlertException;
import com.team5.watchlists.exception.PriceAlertLimitReachedException;
import com.team5.watchlists.exception.PriceAlertNotFoundException;
import com.team5.watchlists.exception.WatchlistItemNotFoundException;
import com.team5.watchlists.mapper.PriceAlertMapper;
import com.team5.watchlists.mapper.WatchlistMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceAlertServiceImpl implements PriceAlertService {

    // How many ACTIVE alerts one account may hold; watchlists.alerts.max-active-per-account overrides it.
    // Every active alert is looked at on every quote for its symbol, so this bounds the consumer's work.
    public static final int DEFAULT_MAX_ACTIVE_ALERTS = 20;

    private final PriceAlertMapper alertMapper;
    private final WatchlistMapper watchlistMapper;
    private final AccountService accountService;
    private final WatchlistAccessGuard accessGuard;
    private final int maxActiveAlerts;
    private final Clock clock;

    @Autowired
    public PriceAlertServiceImpl(
        PriceAlertMapper alertMapper,
        WatchlistMapper watchlistMapper,
        AccountService accountService,
        WatchlistAccessGuard accessGuard,
        @Value("${watchlists.alerts.max-active-per-account:" + DEFAULT_MAX_ACTIVE_ALERTS + "}") int maxActiveAlerts
    ) {
        this(alertMapper, watchlistMapper, accountService, accessGuard, maxActiveAlerts, Clock.systemUTC());
    }

    PriceAlertServiceImpl(
        PriceAlertMapper alertMapper,
        WatchlistMapper watchlistMapper,
        AccountService accountService,
        WatchlistAccessGuard accessGuard,
        int maxActiveAlerts,
        Clock clock
    ) {
        if (maxActiveAlerts < 1) {
            throw new IllegalArgumentException("watchlists.alerts.max-active-per-account must be at least 1");
        }
        this.alertMapper = alertMapper;
        this.watchlistMapper = watchlistMapper;
        this.accountService = accountService;
        this.accessGuard = accessGuard;
        this.maxActiveAlerts = maxActiveAlerts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PriceAlertListResponse listAlerts(long authenticatedAccountId, long accountId) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        List<PriceAlertResponse> alerts = alertMapper.findByAccount(accountId);
        int active = (int) alerts.stream().filter(alert -> alert.status() == PriceAlertStatus.ACTIVE).count();
        return new PriceAlertListResponse(maxActiveAlerts, active, alerts);
    }

    @Override
    @Transactional
    public PriceAlertResponse createAlert(long authenticatedAccountId, long accountId, CreatePriceAlertRequest request) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        // ACC-404 when the account does not exist, ACC-403 when it is not ACTIVE
        accountService.getAccount(authenticatedAccountId, accountId);

        WatchlistEntry entry = watchlistMapper.findWatchlistEntry(request.getWatchlistId());
        if (entry == null) {
            throw new WatchlistItemNotFoundException();
        }
        // an alert on another account's watchlist entry is ACC-403, and nothing is written
        accessGuard.assertSameAccount(authenticatedAccountId, entry.accountId());

        AlertDirection direction = AlertDirection.valueOf(request.getDirection());

        // held until commit: a second create for this account waits here and then sees this one's row
        alertMapper.lockAccountAlerts(accountId);
        // the same level twice would fire on the same quote and send the same message twice
        if (alertMapper.existsActive(accountId, entry.symbol(), direction, request.getThreshold())) {
            throw new DuplicatePriceAlertException();
        }
        if (alertMapper.countActiveByAccount(accountId) >= maxActiveAlerts) {
            throw new PriceAlertLimitReachedException();
        }

        PriceAlertRow alert = new PriceAlertRow();
        alert.setAccountId(accountId);
        alert.setWatchlistId(entry.watchlistId());
        alert.setSymbol(entry.symbol());
        alert.setDirection(direction);
        alert.setThreshold(request.getThreshold());
        alert.setStatus(PriceAlertStatus.ACTIVE);
        alert.setDeliveryId(UUID.randomUUID());
        alert.setCreatedAt(OffsetDateTime.now(clock));
        try {
            if (alertMapper.insert(alert) != 1) {
                throw new OrderConflictException("Price alert insert did not affect exactly one row");
            }
        } catch (DuplicateKeyException e) {
            // uq_price_alerts_active_level: the database's answer if the check above is ever bypassed
            throw new DuplicatePriceAlertException();
        }
        return alertMapper.findForAccount(alert.getAlertId(), accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public PriceAlertResponse getAlert(long authenticatedAccountId, long accountId, long alertId) {
        assertOwnedAlert(authenticatedAccountId, accountId, alertId);
        PriceAlertResponse alert = alertMapper.findForAccount(alertId, accountId);
        if (alert == null) {
            throw new PriceAlertNotFoundException();
        }
        return alert;
    }

    @Override
    @Transactional
    public void deleteAlert(long authenticatedAccountId, long accountId, long alertId) {
        assertOwnedAlert(authenticatedAccountId, accountId, alertId);
        if (alertMapper.deleteForAccount(alertId, accountId) != 1) {
            throw new PriceAlertNotFoundException();
        }
    }

    // the path account must be the token's, and the alert must be the path account's
    private void assertOwnedAlert(long authenticatedAccountId, long accountId, long alertId) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        Long owner = alertMapper.findOwner(alertId);
        if (owner == null) {
            throw new PriceAlertNotFoundException();
        }
        accessGuard.assertSameAccount(authenticatedAccountId, owner);
    }
}
