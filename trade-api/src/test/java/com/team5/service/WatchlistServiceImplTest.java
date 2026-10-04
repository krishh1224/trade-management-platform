package com.team5.service;

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
import com.team5.domain.exception.WatchlistDuplicateException;
import com.team5.domain.exception.WatchlistItemNotFoundException;
import com.team5.dto.request.AddToWatchlistRequest;
import com.team5.dto.response.WatchlistItemResponse;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.OrderMapper;
import com.team5.mapper.WatchlistMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceImplTest {

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private AccountQueryMapper accountQueryMapper;

    @Mock
    private OrderMapper orderMapper;

    private WatchlistServiceImpl watchlistService;

    @BeforeEach
    void setUp() {
        watchlistService = new WatchlistServiceImpl(
            watchlistMapper,
            accountQueryMapper,
            orderMapper,
            new AccountAccessAuthorizer()
        );
    }

    @Test
    void testAddToWatchlist_Success() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");

        Map<String, Object> account = new HashMap<>();
        account.put("status", "ACTIVE");

        Map<String, Object> instrument = new HashMap<>();
        instrument.put("instrument_id", 302L);
        instrument.put("name", "Reliance Industries Limited");
        instrument.put("tradable", true);

        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);
        when(orderMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(instrument);
        when(watchlistMapper.findDefaultWatchlistGroupId(101L)).thenReturn(5001L);
        when(watchlistMapper.findWatchlistGroups(101L)).thenReturn(List.of());
        when(watchlistMapper.existsInWatchlist(5001L, 302L)).thenReturn(false);
        when(watchlistMapper.insertWatchlistItem(anyLong(), eq(101L), eq(5001L), eq(302L), any())).thenReturn(1);

        WatchlistItemResponse response = watchlistService.addToWatchlist(101L, request);

        assertEquals(302L, response.instrumentId());
        assertEquals("RELIANCE.NS", response.symbol());
        assertEquals("Reliance Industries Limited", response.instrumentName());
    }

    @Test
    void testAddToWatchlist_UnauthorizedAccount() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");

        assertThrows(AccountAccessDeniedException.class,
            () -> watchlistService.addToWatchlist(999L, request));

        verifyNoInteractions(accountQueryMapper, orderMapper, watchlistMapper);
    }

    @Test
    void testAddToWatchlist_AccountNotFound() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class,
            () -> watchlistService.addToWatchlist(101L, request));
    }

    @Test
    void testAddToWatchlist_AccountNotActive() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");

        Map<String, Object> account = new HashMap<>();
        account.put("status", "SUSPENDED");
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);

        assertThrows(AccountNotActiveException.class,
            () -> watchlistService.addToWatchlist(101L, request));
    }

    @Test
    void testAddToWatchlist_InstrumentNotFound() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");

        Map<String, Object> account = new HashMap<>();
        account.put("status", "ACTIVE");
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);
        when(orderMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(null);

        assertThrows(InstrumentNotFoundException.class,
            () -> watchlistService.addToWatchlist(101L, request));
    }

    @Test
    void testAddToWatchlist_InstrumentNotTradable() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");

        Map<String, Object> account = new HashMap<>();
        account.put("status", "ACTIVE");

        Map<String, Object> instrument = new HashMap<>();
        instrument.put("instrument_id", 302L);
        instrument.put("name", "Reliance Industries Limited");
        instrument.put("tradable", false);

        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);
        when(orderMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(instrument);

        assertThrows(InstrumentNotFoundException.class,
            () -> watchlistService.addToWatchlist(101L, request));
    }

    @Test
    void testAddToWatchlist_Duplicate() {
        AddToWatchlistRequest request = request(101L, "RELIANCE.NS");

        Map<String, Object> account = new HashMap<>();
        account.put("status", "ACTIVE");

        Map<String, Object> instrument = new HashMap<>();
        instrument.put("instrument_id", 302L);
        instrument.put("name", "Reliance Industries Limited");
        instrument.put("tradable", true);

        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(account);
        when(orderMapper.findInstrumentByTicker("RELIANCE.NS")).thenReturn(instrument);
        when(watchlistMapper.findDefaultWatchlistGroupId(101L)).thenReturn(5001L);
        when(watchlistMapper.existsInWatchlist(5001L, 302L)).thenReturn(true);

        assertThrows(WatchlistDuplicateException.class,
            () -> watchlistService.addToWatchlist(101L, request));
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
            null
        );
        when(watchlistMapper.findWatchlistByAccountId(101L, null)).thenReturn(List.of(item));

        List<WatchlistItemResponse> items = watchlistService.listWatchlist(101L);

        assertEquals(1, items.size());
        assertEquals("RELIANCE.NS", items.get(0).symbol());
    }

    @Test
    void testListWatchlist_Empty() {
        when(watchlistMapper.findWatchlistByAccountId(101L, null)).thenReturn(List.of());

        List<WatchlistItemResponse> items = watchlistService.listWatchlist(101L);

        assertTrue(items.isEmpty());
    }

    @Test
    void testRemoveFromWatchlist_Success() {
        when(watchlistMapper.findAccountIdByWatchlistId(101L, 302L)).thenReturn(101L);
        when(watchlistMapper.deleteWatchlistItem(101L, 302L)).thenReturn(1);

        watchlistService.removeFromWatchlist(101L, 302L);

        verify(watchlistMapper).deleteWatchlistItem(101L, 302L);
    }

    @Test
    void testRemoveFromWatchlist_NotFound() {
        when(watchlistMapper.findAccountIdByWatchlistId(101L, 302L)).thenReturn(null);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.removeFromWatchlist(101L, 302L));

        verify(watchlistMapper, never()).deleteWatchlistItem(anyLong(), anyLong());
    }

    @Test
    void testRemoveFromWatchlist_DeleteFails() {
        when(watchlistMapper.findAccountIdByWatchlistId(101L, 302L)).thenReturn(101L);
        when(watchlistMapper.deleteWatchlistItem(101L, 302L)).thenReturn(0);

        assertThrows(WatchlistItemNotFoundException.class,
            () -> watchlistService.removeFromWatchlist(101L, 302L));
    }

    private static AddToWatchlistRequest request(long accountId, String symbol) {
        AddToWatchlistRequest request = new AddToWatchlistRequest();
        request.setAccountId(accountId);
        request.setSymbol(symbol);
        return request;
    }
}
