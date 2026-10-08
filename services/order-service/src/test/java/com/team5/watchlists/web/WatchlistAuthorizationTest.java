package com.team5.watchlists.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.service.AccountService;
import com.team5.watchlists.domain.WatchlistEntry;
import com.team5.watchlists.domain.WatchlistGroupRef;
import com.team5.watchlists.domain.WatchlistInstrument;
import com.team5.watchlists.dto.WatchlistItemResponse;
import com.team5.watchlists.mapper.WatchlistMapper;
import com.team5.watchlists.service.WatchlistAccessGuard;
import com.team5.watchlists.service.WatchlistServiceImpl;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

// Watchlist routes with the real service behind them: every way of addressing another account's
// watchlist (body accountId, a group id, an entry id) is the platform's ACC-403, and nothing is read
// or written. WatchlistControllerTest covers the happy paths against a mocked service.
@WebMvcTest(WatchlistController.class)
@Import({
    SecurityConfig.class,
    AuthWebMvcConfig.class,
    WatchlistServiceImpl.class,
    WatchlistAccessGuard.class,
    AccountAccessAuthorizer.class
})
class WatchlistAuthorizationTest {

    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WatchlistMapper watchlistMapper;

    @MockBean
    private AccountService accountService;

    @Test
    void createdWatchlistAndEntry_areScopedToTheTokenAccount() throws Exception {
        // Mockito would answer 0L for a Long; the SQL answers null when the name is free
        when(watchlistMapper.findWatchlistGroupByName(OWNER, "Swing ideas")).thenReturn(null);
        when(watchlistMapper.insertWatchlistGroup(anyLong(), eq(OWNER), eq("Swing ideas"), any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/watchlists/groups")
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Swing ideas\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Swing ideas"));

        verify(watchlistMapper).insertWatchlistGroup(anyLong(), eq(OWNER), eq("Swing ideas"), any());
    }

    @Test
    void addedInstrument_isListedWithItsLivePrice() throws Exception {
        when(watchlistMapper.findInstrumentByTicker("INFY.NS"))
            .thenReturn(new WatchlistInstrument(301L, "INFY.NS", "Infosys Limited", true, "INR"));
        when(watchlistMapper.findDefaultWatchlistGroupId(OWNER)).thenReturn(5001L);
        when(watchlistMapper.findWatchlistGroups(OWNER)).thenReturn(List.of());
        when(watchlistMapper.insertWatchlistItem(anyLong(), eq(OWNER), eq(5001L), eq(301L), any())).thenReturn(1);
        when(watchlistMapper.findWatchlistByAccountId(OWNER, null)).thenReturn(List.of(new WatchlistItemResponse(
            7001L, 5001L, "Favourite", 301L, "INFY.NS", "Infosys Limited",
            OffsetDateTime.parse("2026-10-07T09:00:00Z"), new BigDecimal("1502.3500"), new BigDecimal("1.25"), "INR")));

        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 101, \"symbol\": \"INFY.NS\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/watchlists").with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("INFY.NS"))
            .andExpect(jsonPath("$[0].lastPrice").value(1502.35))
            .andExpect(jsonPath("$[0].currency").value("INR"));
        // the list is read for the token's account only
        verify(watchlistMapper).findWatchlistByAccountId(OWNER, null);
    }

    @Test
    void addToWatchlist_forAnotherAccount_403AndNothingReadOrWritten() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(OTHER_ACCOUNT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 101, \"symbol\": \"INFY.NS\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(watchlistMapper, accountService);
    }

    @Test
    void addToWatchlist_intoAnotherAccountsGroup_403() throws Exception {
        when(watchlistMapper.findInstrumentByTicker("INFY.NS"))
            .thenReturn(new WatchlistInstrument(301L, "INFY.NS", "Infosys Limited", true, "INR"));
        when(watchlistMapper.findWatchlistGroupOwner(9001L)).thenReturn(OTHER_ACCOUNT);

        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 101, \"symbol\": \"INFY.NS\", \"watchlistGroupId\": \"9001\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(watchlistMapper, never()).insertWatchlistItem(any(), any(), any(), any(), any());
    }

    @Test
    void listAnotherAccountsWatchlistGroup_403AndNothingRead() throws Exception {
        when(watchlistMapper.findWatchlistGroupOwner(9001L)).thenReturn(OTHER_ACCOUNT);

        mockMvc.perform(get("/api/v1/watchlists").param("groupId", "9001").with(authenticatedAs(OWNER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(watchlistMapper, never()).findWatchlistByAccountId(any(), any());
    }

    @Test
    void removeAnotherAccountsEntry_403AndNothingDeleted() throws Exception {
        when(watchlistMapper.findWatchlistEntry(7001L))
            .thenReturn(new WatchlistEntry(7001L, OTHER_ACCOUNT, "INFY.NS", "Infosys Limited"));

        mockMvc.perform(delete("/api/v1/watchlists/{watchlistId}", 7001L).with(authenticatedAs(OWNER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(watchlistMapper, never()).deleteWatchlistItem(any(), any());
    }

    @Test
    void deleteCustomWatchlist_204() throws Exception {
        when(watchlistMapper.findWatchlistGroup(5002L)).thenReturn(new WatchlistGroupRef(5002L, OWNER, "Swing ideas"));
        when(watchlistMapper.deleteWatchlistGroup(OWNER, 5002L)).thenReturn(1);

        mockMvc.perform(delete("/api/v1/watchlists/groups/{groupId}", 5002L).with(authenticatedAs(OWNER)))
            .andExpect(status().isNoContent());

        verify(watchlistMapper).deleteWatchlistGroup(OWNER, 5002L);
    }

    @Test
    void deleteFavouriteWatchlist_409AndNothingDeleted() throws Exception {
        when(watchlistMapper.findWatchlistGroup(5001L)).thenReturn(new WatchlistGroupRef(5001L, OWNER, "Favourite"));

        mockMvc.perform(delete("/api/v1/watchlists/groups/{groupId}", 5001L).with(authenticatedAs(OWNER)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("WTL-409"))
            .andExpect(jsonPath("$.message").value("The Favourite watchlist cannot be deleted"));

        verify(watchlistMapper, never()).deleteWatchlistGroup(any(), any());
    }

    @Test
    void deleteAnotherAccountsWatchlist_403AndNothingDeleted() throws Exception {
        when(watchlistMapper.findWatchlistGroup(9001L)).thenReturn(new WatchlistGroupRef(9001L, OTHER_ACCOUNT, "Theirs"));

        mockMvc.perform(delete("/api/v1/watchlists/groups/{groupId}", 9001L).with(authenticatedAs(OWNER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(watchlistMapper, never()).deleteWatchlistGroup(any(), any());
    }

    @Test
    void removeUnknownEntry_404() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/{watchlistId}", 7001L).with(authenticatedAs(OWNER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("WTL-404"));
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token
            .claim("accountId", accountId)
            .claim("iss", "auth-service")
        );
    }
}
