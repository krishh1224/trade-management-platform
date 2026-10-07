package com.team5.watchlists.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.watchlists.exception.WatchlistDuplicateException;
import com.team5.watchlists.exception.WatchlistItemNotFoundException;
import com.team5.watchlists.dto.AddToWatchlistRequest;
import com.team5.watchlists.dto.WatchlistItemResponse;
import com.team5.watchlists.service.WatchlistService;
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

@WebMvcTest(WatchlistController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class})
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WatchlistService watchlistService;

    @Test
    void testAddToWatchlist_Success_201() throws Exception {
        when(watchlistService.addToWatchlist(eq(101L), any())).thenReturn(new WatchlistItemResponse(
            7001L,
            5001L,
            "Default",
            301L,
            "INFY.NS",
            "Infosys Limited",
            OffsetDateTime.parse("2026-09-29T09:14:22Z"),
            BigDecimal.valueOf(2500.00),
            BigDecimal.valueOf(2.50),
            "INR"
        ));

        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": 101,
                      "symbol": "INFY.NS"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.watchlistId").value(7001))
            .andExpect(jsonPath("$.instrumentId").value(301))
            .andExpect(jsonPath("$.symbol").value("INFY.NS"));
    }

    @Test
    void testAddToWatchlist_Duplicate_409() throws Exception {
        when(watchlistService.addToWatchlist(eq(101L), any())).thenThrow(new WatchlistDuplicateException());

        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": 101,
                      "symbol": "INFY.NS"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("WTL-409"));
    }

    @Test
    void testAddToWatchlist_InvalidInput_422() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": 0,
                      "symbol": ""
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void testAddToWatchlist_InvalidSymbol_422() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists")
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": 101,
                      "symbol": " "
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void testListWatchlist_Empty_200() throws Exception {
        when(watchlistService.listWatchlist(eq(101L), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/watchlists")
                .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testListWatchlist_Success_200() throws Exception {
        when(watchlistService.listWatchlist(eq(101L), any())).thenReturn(List.of(
            new WatchlistItemResponse(7001L, 5001L, "Default", 301L, "INFY.NS", "Infosys Limited", OffsetDateTime.parse("2026-09-29T09:14:22Z"), BigDecimal.valueOf(2500.00), BigDecimal.valueOf(2.50), "INR"),
            new WatchlistItemResponse(7002L, 5001L, "Default", 302L, "RELIANCE.NS", "Reliance Industries Limited", OffsetDateTime.parse("2026-09-29T09:15:22Z"), BigDecimal.valueOf(2800.00), BigDecimal.valueOf(1.75), "INR")
        ));

        mockMvc.perform(get("/api/v1/watchlists")
                .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testRemoveFromWatchlist_Success_204() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/301")
                .with(authenticatedAs(101L)))
            .andExpect(status().isNoContent());
    }

    @Test
    void testRemoveFromWatchlist_NotFound_404() throws Exception {
        org.mockito.Mockito.doThrow(new WatchlistItemNotFoundException())
            .when(watchlistService).removeFromWatchlist(101L, 999L);

        mockMvc.perform(delete("/api/v1/watchlists/999")
                .with(authenticatedAs(101L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("WTL-404"));
    }

    @Test
    void testAddToWatchlist_NoAuth_401() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": 101,
                      "symbol": "INFY.NS"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token
            .claim("accountId", accountId)
            .claim("iss", "auth-service")
        );
    }

    @SuppressWarnings("unused")
    private static AddToWatchlistRequest addRequest(long accountId, String symbol) {
        AddToWatchlistRequest request = new AddToWatchlistRequest();
        request.setAccountId(accountId);
        request.setSymbol(symbol);
        return request;
    }
}
