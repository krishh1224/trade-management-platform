package com.team5.watchlists.service;

import static com.team5.notifications.api.AlertDirection.ABOVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.service.AccountService;
import com.team5.watchlists.InMemoryAlertStore;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PriceAlertServiceImplTest {

    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;
    private static final long ENTRY = 7318826194416337412L;
    private static final int CAP = 3;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T09:30:00Z"), ZoneOffset.UTC);

    private InMemoryAlertStore store;
    private PriceAlertMapper alertMapper;
    private WatchlistMapper watchlistMapper;
    private AccountService accountService;
    private PriceAlertServiceImpl service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAlertStore();
        alertMapper = spy(store.alertMapper);
        watchlistMapper = mock(WatchlistMapper.class);
        accountService = mock(AccountService.class);
        service = new PriceAlertServiceImpl(alertMapper, watchlistMapper, accountService,
            new WatchlistAccessGuard(new AccountAccessAuthorizer()), CAP, CLOCK);
        when(watchlistMapper.findWatchlistEntry(ENTRY))
            .thenReturn(new WatchlistEntry(ENTRY, OWNER, "INFY.NS", "Infosys Limited"));
    }

    @Test
    void createAlert_isScopedToTheAccountAndStartsActive() {
        PriceAlertResponse created = service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));

        assertNotNull(created.alertId());
        assertEquals(PriceAlertStatus.ACTIVE, created.status());
        assertEquals(ABOVE, created.direction());
        assertEquals("INFY.NS", created.symbol(), "the symbol comes from the watchlist entry");
        assertEquals(ENTRY, created.watchlistId());
        assertEquals(OWNER, store.alert(created.alertId()).getAccountId());
        assertNotNull(store.alert(created.alertId()).getDeliveryId());
        verify(accountService).getAccount(OWNER, OWNER);
    }

    @Test
    void createAlert_beyondTheCap_isRejectedAndNothingIsWritten() {
        for (int i = 0; i < CAP; i++) {
            service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", String.valueOf(1500 + i)));
        }

        PriceAlertLimitReachedException refused = assertThrows(PriceAlertLimitReachedException.class,
            () -> service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1600.00")));

        assertEquals("VAL-422", refused.getCode());
        assertEquals(CAP, store.alertMapper.countActiveByAccount(OWNER));
    }

    @Test
    void createAlert_triggeredAlertsDoNotCountTowardsTheCap() {
        for (int i = 0; i < CAP; i++) {
            service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", String.valueOf(1500 + i)));
        }
        store.alertMapper.markTriggered(1L, new BigDecimal("1501.00"), CLOCK.instant().atOffset(ZoneOffset.UTC));

        assertNotNull(service.createAlert(OWNER, OWNER, request(ENTRY, "BELOW", "1400.00")));
    }

    @Test
    void createAlert_sameLevelTwice_isRefusedAndNothingIsWritten() {
        service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));

        // 1500 and 1500.00 are the same level
        DuplicatePriceAlertException refused = assertThrows(DuplicatePriceAlertException.class,
            () -> service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500")));

        assertEquals("WTL-409", refused.getCode());
        assertEquals(1, store.alertMapper.countActiveByAccount(OWNER));
    }

    @Test
    void createAlert_sameSymbolFromAnotherWatchlist_isStillADuplicate() {
        long sameSymbolElsewhere = 99L;
        when(watchlistMapper.findWatchlistEntry(sameSymbolElsewhere))
            .thenReturn(new WatchlistEntry(sameSymbolElsewhere, OWNER, "INFY.NS", "Infosys Limited"));
        service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));

        assertThrows(DuplicatePriceAlertException.class,
            () -> service.createAlert(OWNER, OWNER, request(sameSymbolElsewhere, "ABOVE", "1500.00")));
    }

    @Test
    void createAlert_otherDirectionOrLevel_isNotADuplicate() {
        service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));

        assertNotNull(service.createAlert(OWNER, OWNER, request(ENTRY, "BELOW", "1500.00")));
        assertNotNull(service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.05")));
    }

    @Test
    void createAlert_sameLevelAfterTheFirstHasTriggered_isAllowed() {
        long first = service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00")).alertId();
        store.alertMapper.markTriggered(first, new BigDecimal("1502.00"), CLOCK.instant().atOffset(ZoneOffset.UTC));

        assertNotNull(service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00")));
    }

    @Test
    void createAlert_sameLevelForAnotherAccount_isNotADuplicate() {
        long otherEntry = 99L;
        when(watchlistMapper.findWatchlistEntry(otherEntry))
            .thenReturn(new WatchlistEntry(otherEntry, OTHER_ACCOUNT, "INFY.NS", "Infosys Limited"));
        service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));

        assertNotNull(service.createAlert(OTHER_ACCOUNT, OTHER_ACCOUNT, request(otherEntry, "ABOVE", "1500.00")));
    }

    @Test
    void createAlert_databaseUniqueViolation_isReportedAsADuplicate() {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("uq_price_alerts_active_level"))
            .when(alertMapper).insert(org.mockito.ArgumentMatchers.any());

        assertThrows(DuplicatePriceAlertException.class,
            () -> service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00")));
    }

    @Test
    void createAlert_takesTheAccountLockBeforeCounting() {
        service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));

        InOrder order = inOrder(alertMapper);
        order.verify(alertMapper).lockAccountAlerts(OWNER);
        order.verify(alertMapper).existsActive(OWNER, "INFY.NS", ABOVE, new BigDecimal("1500.00"));
        order.verify(alertMapper).countActiveByAccount(OWNER);
        order.verify(alertMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAlert_capIsPerAccount() {
        for (int i = 0; i < CAP; i++) {
            service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", String.valueOf(1500 + i)));
        }
        long otherEntry = 99L;
        when(watchlistMapper.findWatchlistEntry(otherEntry))
            .thenReturn(new WatchlistEntry(otherEntry, OTHER_ACCOUNT, "INFY.NS", "Infosys Limited"));

        assertNotNull(service.createAlert(OTHER_ACCOUNT, OTHER_ACCOUNT, request(otherEntry, "ABOVE", "1500.00")));
    }

    @Test
    void createAlert_forAnotherAccountsPath_403AndNothingReadOrWritten() {
        assertThrows(AccountAccessDeniedException.class,
            () -> service.createAlert(OTHER_ACCOUNT, OWNER, request(ENTRY, "ABOVE", "1500.00")));

        verifyNoInteractions(accountService, watchlistMapper);
        verify(alertMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAlert_onAnotherAccountsWatchlistEntry_403AndNothingWritten() {
        long theirEntry = 99L;
        when(watchlistMapper.findWatchlistEntry(theirEntry))
            .thenReturn(new WatchlistEntry(theirEntry, OTHER_ACCOUNT, "TCS.NS", "Tata Consultancy Services"));

        assertThrows(AccountAccessDeniedException.class,
            () -> service.createAlert(OWNER, OWNER, request(theirEntry, "ABOVE", "1500.00")));

        verify(alertMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAlert_onUnknownEntry_404() {
        assertThrows(WatchlistItemNotFoundException.class,
            () -> service.createAlert(OWNER, OWNER, request(12345L, "ABOVE", "1500.00")));
    }

    @Test
    void createAlert_onInactiveAccount_isRefused() {
        when(accountService.getAccount(OWNER, OWNER)).thenThrow(new AccountNotActiveException());

        assertThrows(AccountNotActiveException.class,
            () -> service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00")));
        verify(alertMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listAlerts_showsEachAlertsStateAndTheLimit() {
        service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00"));
        service.createAlert(OWNER, OWNER, request(ENTRY, "BELOW", "1400.00"));
        store.alertMapper.markTriggered(1L, new BigDecimal("1502.35"), CLOCK.instant().atOffset(ZoneOffset.UTC));

        PriceAlertListResponse list = service.listAlerts(OWNER, OWNER);

        assertEquals(CAP, list.maxActiveAlerts());
        assertEquals(1, list.activeAlerts());
        assertEquals(PriceAlertStatus.ACTIVE, list.alerts().get(0).status());
        assertEquals(PriceAlertStatus.TRIGGERED, list.alerts().get(1).status());
        assertEquals(new BigDecimal("1502.35"), list.alerts().get(1).triggerPrice());
    }

    @Test
    void listAlerts_ofAnotherAccount_403AndNothingRead() {
        assertThrows(AccountAccessDeniedException.class, () -> service.listAlerts(OTHER_ACCOUNT, OWNER));

        verify(alertMapper, never()).findByAccount(anyLong());
    }

    @Test
    void getOrDeleteAnotherAccountsAlert_403AndNothingDeleted() {
        long theirEntry = 99L;
        when(watchlistMapper.findWatchlistEntry(theirEntry))
            .thenReturn(new WatchlistEntry(theirEntry, OTHER_ACCOUNT, "TCS.NS", "Tata Consultancy Services"));
        long theirAlert = service.createAlert(OTHER_ACCOUNT, OTHER_ACCOUNT, request(theirEntry, "ABOVE", "10")).alertId();

        // the caller addresses their own account in the path but names someone else's alert
        assertThrows(AccountAccessDeniedException.class, () -> service.getAlert(OWNER, OWNER, theirAlert));
        assertThrows(AccountAccessDeniedException.class, () -> service.deleteAlert(OWNER, OWNER, theirAlert));

        verify(alertMapper, never()).deleteForAccount(anyLong(), anyLong());
        assertNotNull(store.alert(theirAlert));
    }

    @Test
    void deleteAlert_removesIt_andAnUnknownAlertIs404() {
        long alertId = service.createAlert(OWNER, OWNER, request(ENTRY, "ABOVE", "1500.00")).alertId();

        service.deleteAlert(OWNER, OWNER, alertId);

        assertThrows(PriceAlertNotFoundException.class, () -> service.getAlert(OWNER, OWNER, alertId));
        assertThrows(PriceAlertNotFoundException.class, () -> service.deleteAlert(OWNER, OWNER, alertId));
    }

    @Test
    void aCapBelowOne_isAConfigurationError() {
        assertThrows(IllegalArgumentException.class, () -> new PriceAlertServiceImpl(alertMapper, watchlistMapper,
            accountService, new WatchlistAccessGuard(new AccountAccessAuthorizer()), 0, CLOCK));
    }

    private static CreatePriceAlertRequest request(long watchlistId, String direction, String threshold) {
        CreatePriceAlertRequest request = new CreatePriceAlertRequest();
        request.setWatchlistId(watchlistId);
        request.setDirection(direction);
        request.setThreshold(new BigDecimal(threshold));
        return request;
    }
}
