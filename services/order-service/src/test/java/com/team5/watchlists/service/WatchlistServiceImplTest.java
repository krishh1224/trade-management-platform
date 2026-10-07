package com.team5.watchlists.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.service.AccountService;
import com.team5.watchlists.domain.WatchlistEntry;
import com.team5.watchlists.domain.WatchlistGroupRef;
import com.team5.watchlists.domain.WatchlistInstrument;
import com.team5.watchlists.dto.AddToWatchlistRequest;
import com.team5.watchlists.dto.WatchlistItemResponse;
import com.team5.watchlists.exception.FavouriteWatchlistNotDeletableException;
import com.team5.watchlists.exception.WatchlistDuplicateException;
import com.team5.watchlists.exception.WatchlistItemNotFoundException;
import com.team5.watchlists.mapper.WatchlistMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceImplTest {

    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private AccountService accountService;

    private WatchlistServiceImpl watchlistService;

    @BeforeEach
    void setUp() {
        watchlistService = new WatchlistServiceImpl(
            watchlistMapper,
            accountService,
            new WatchlistAccessGuard(new AccountAccessAuthorizer())
        );
    }

    @Test
    void testAddToWatchlist_Success() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");

        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(reliance(true));
        when(watchlistMapper.findDefaultWatchlistGroupId(OWNER)).thenReturn(5001L);
        when(watchlistMapper.findWatchlistGroups(OWNER)).thenReturn(List.of());
        when(watchlistMapper.existsInWatchlist(5001L, 302L)).thenReturn(false);
        when(watchlistMapper.insertWatchlistItem(anyLong(), eq(OWNER), eq(5001L), eq(302L), any())).thenReturn(1);

        WatchlistItemResponse response = watchlistService.addToWatchlist(OWNER, request);

        assertEquals(302L, response.instrumentId());
        assertEquals("RELIANCE.NS", response.symbol());
        assertEquals("Reliance Industries Limited", response.instrumentName());
        assertEquals("INR", response.currency());
        // the account is checked through the accounts module's interface, not its tables
        verify(accountService).getAccount(OWNER, OWNER);
    }

    // Watchlists are independent of trading: no position or order is looked at to watch an instrument
    @Test
    void testAddToWatchlist_NeedsNoPositionOrOrder() {
        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(reliance(true));
        when(watchlistMapper.findDefaultWatchlistGroupId(OWNER)).thenReturn(5001L);
        when(watchlistMapper.findWatchlistGroups(OWNER)).thenReturn(List.of());
        when(watchlistMapper.insertWatchlistItem(anyLong(), eq(OWNER), eq(5001L), eq(302L), any())).thenReturn(1);

        watchlistService.addToWatchlist(OWNER, request(OWNER, "RELIANCE.NS"));

        verify(accountService, never()).getPositions(anyLong(), any());
        verify(accountService, never()).getOrders(anyLong(), any(), any(), any(), any());
    }

    @Test
    void testAddToWatchlist_UnauthorizedAccount() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");

        assertThrows(AccountAccessDeniedException.class,
            () -> watchlistService.addToWatchlist(999L, request));

        verifyNoInteractions(accountService, watchlistMapper);
    }

    @Test
    void testAddToWatchlist_AccountNotFound() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");
        when(accountService.getAccount(OWNER, OWNER)).thenThrow(new AccountNotFoundException());

        assertThrows(AccountNotFoundException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));
        verifyNoInteractions(watchlistMapper);
    }

    @Test
    void testAddToWatchlist_AccountNotActive() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");
        when(accountService.getAccount(OWNER, OWNER)).thenThrow(new AccountNotActiveException());

        assertThrows(AccountNotActiveException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));
        verifyNoInteractions(watchlistMapper);
    }

    @Test
    void testAddToWatchlist_InstrumentNotFound() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");
        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(null);

        assertThrows(InstrumentNotFoundException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));
    }

    @Test
    void testAddToWatchlist_InstrumentNotTradable() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");
        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(reliance(false));

        assertThrows(InstrumentNotFoundException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));
    }

    @Test
    void testAddToWatchlist_Duplicate() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");

        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(reliance(true));
        when(watchlistMapper.findDefaultWatchlistGroupId(OWNER)).thenReturn(5001L);
        when(watchlistMapper.existsInWatchlist(5001L, 302L)).thenReturn(true);

        assertThrows(WatchlistDuplicateException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));
    }

    @Test
    void testAddToWatchlist_IntoAnotherAccountsGroup_403AndNothingInserted() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");
        request.setWatchlistGroupId(9001L);
        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(reliance(true));
        when(watchlistMapper.findWatchlistGroupOwner(9001L)).thenReturn(OTHER_ACCOUNT);

        assertThrows(AccountAccessDeniedException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));

        verify(watchlistMapper, never()).insertWatchlistItem(any(), any(), any(), any(), any());
    }

    @Test
    void testAddToWatchlist_IntoUnknownGroup_404() {
        AddToWatchlistRequest request = request(OWNER, "RELIANCE.NS");
        request.setWatchlistGroupId(9001L);
        when(watchlistMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(reliance(true));
        when(watchlistMapper.findWatchlistGroupOwner(9001L)).thenReturn(null);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.addToWatchlist(OWNER, request));
    }

    @Test
    void testListWatchlist_Success() {
        WatchlistItemResponse item = new WatchlistItemResponse(
            1L,
            5001L,
            "Default",
            302L,
            "RELIANCE.NS",
            "Reliance Industries Limited",
            null,
            new BigDecimal("2875.4500"),
            BigDecimal.ZERO,
            "INR"
        );
        when(watchlistMapper.findWatchlistByAccountId(OWNER, null)).thenReturn(List.of(item));

        List<WatchlistItemResponse> items = watchlistService.listWatchlist(OWNER);

        assertEquals(1, items.size());
        assertEquals("RELIANCE.NS", items.get(0).symbol());
        assertEquals(new BigDecimal("2875.4500"), items.get(0).lastPrice());
    }

    @Test
    void testListWatchlist_Empty() {
        when(watchlistMapper.findWatchlistByAccountId(OWNER, null)).thenReturn(List.of());

        List<WatchlistItemResponse> items = watchlistService.listWatchlist(OWNER);

        assertTrue(items.isEmpty());
    }

    @Test
    void testListWatchlist_OwnGroup_ReadsOnlyThatGroupOfTheTokenAccount() {
        when(watchlistMapper.findWatchlistGroupOwner(5001L)).thenReturn(OWNER);
        when(watchlistMapper.findWatchlistByAccountId(OWNER, 5001L)).thenReturn(List.of());

        watchlistService.listWatchlist(OWNER, 5001L);

        verify(watchlistMapper).findWatchlistByAccountId(OWNER, 5001L);
    }

    @Test
    void testListWatchlist_AnotherAccountsGroup_403AndNothingRead() {
        when(watchlistMapper.findWatchlistGroupOwner(9001L)).thenReturn(OTHER_ACCOUNT);

        assertThrows(AccountAccessDeniedException.class,
            () -> watchlistService.listWatchlist(OWNER, 9001L));

        verify(watchlistMapper, never()).findWatchlistByAccountId(any(), any());
    }

    @Test
    void testListWatchlist_UnknownGroup_404() {
        when(watchlistMapper.findWatchlistGroupOwner(9001L)).thenReturn(null);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.listWatchlist(OWNER, 9001L));
    }

    @Test
    void testRemoveFromWatchlist_Success() {
        when(watchlistMapper.findWatchlistEntry(302L)).thenReturn(entry(302L, OWNER));
        when(watchlistMapper.deleteWatchlistItem(OWNER, 302L)).thenReturn(1);

        watchlistService.removeFromWatchlist(OWNER, 302L);

        verify(watchlistMapper).deleteWatchlistItem(OWNER, 302L);
    }

    @Test
    void testRemoveFromWatchlist_NotFound() {
        when(watchlistMapper.findWatchlistEntry(302L)).thenReturn(null);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.removeFromWatchlist(OWNER, 302L));

        verify(watchlistMapper, never()).deleteWatchlistItem(anyLong(), anyLong());
    }

    @Test
    void testRemoveFromWatchlist_AnotherAccountsEntry_403AndNothingDeleted() {
        when(watchlistMapper.findWatchlistEntry(302L)).thenReturn(entry(302L, OTHER_ACCOUNT));

        assertThrows(AccountAccessDeniedException.class,
            () -> watchlistService.removeFromWatchlist(OWNER, 302L));

        verify(watchlistMapper, never()).deleteWatchlistItem(anyLong(), anyLong());
    }

    @Test
    void testRemoveFromWatchlist_DeleteFails() {
        when(watchlistMapper.findWatchlistEntry(302L)).thenReturn(entry(302L, OWNER));
        when(watchlistMapper.deleteWatchlistItem(OWNER, 302L)).thenReturn(0);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.removeFromWatchlist(OWNER, 302L));
    }

    @Test
    void testDeleteWatchlistGroup_CustomGroup_IsDeleted() {
        when(watchlistMapper.findWatchlistGroup(5002L)).thenReturn(new WatchlistGroupRef(5002L, OWNER, "Swing ideas"));
        when(watchlistMapper.deleteWatchlistGroup(OWNER, 5002L)).thenReturn(1);

        watchlistService.deleteWatchlistGroup(OWNER, 5002L);

        verify(watchlistMapper).deleteWatchlistGroup(OWNER, 5002L);
    }

    @Test
    void testDeleteWatchlistGroup_Favourite_IsRefusedWhateverItsCase() {
        for (String name : List.of("Favourite", "favourite", "FAVOURITE")) {
            when(watchlistMapper.findWatchlistGroup(5001L)).thenReturn(new WatchlistGroupRef(5001L, OWNER, name));

            FavouriteWatchlistNotDeletableException refused = assertThrows(FavouriteWatchlistNotDeletableException.class,
                () -> watchlistService.deleteWatchlistGroup(OWNER, 5001L));
            assertEquals("WTL-409", refused.getCode());
        }
        verify(watchlistMapper, never()).deleteWatchlistGroup(any(), any());
    }

    @Test
    void testDeleteWatchlistGroup_AnotherAccounts_403AndNothingDeleted() {
        when(watchlistMapper.findWatchlistGroup(9001L)).thenReturn(new WatchlistGroupRef(9001L, OTHER_ACCOUNT, "Theirs"));

        assertThrows(AccountAccessDeniedException.class,
            () -> watchlistService.deleteWatchlistGroup(OWNER, 9001L));

        verify(watchlistMapper, never()).deleteWatchlistGroup(any(), any());
    }

    @Test
    void testDeleteWatchlistGroup_Unknown_404() {
        when(watchlistMapper.findWatchlistGroup(9001L)).thenReturn(null);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.deleteWatchlistGroup(OWNER, 9001L));
    }

    private static WatchlistInstrument reliance(boolean tradable) {
        return new WatchlistInstrument(302L, "RELIANCE.NS", "Reliance Industries Limited", tradable, "INR");
    }

    private static WatchlistEntry entry(long watchlistId, long ownerAccountId) {
        return new WatchlistEntry(watchlistId, ownerAccountId, "RELIANCE.NS", "Reliance Industries Limited");
    }

    private static AddToWatchlistRequest request(long accountId, String symbol) {
        AddToWatchlistRequest request = new AddToWatchlistRequest();
        request.setAccountId(accountId);
        request.setSymbol(symbol);
        return request;
    }
}
