package com.team5.watchlists.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import com.team5.notifications.api.AlertDirection;
import com.team5.service.AccountService;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.PriceAlertStatus;
import com.team5.watchlists.domain.WatchlistEntry;
import com.team5.watchlists.dto.PriceAlertResponse;
import com.team5.watchlists.mapper.PriceAlertMapper;
import com.team5.watchlists.mapper.WatchlistMapper;
import com.team5.watchlists.service.PriceAlertServiceImpl;
import com.team5.watchlists.service.WatchlistAccessGuard;
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

// The real service, guard and authorizer sit behind the controller, so a 403 here is the actual
// ownership check refusing, not a mock told to throw.
@WebMvcTest(PriceAlertController.class)
@Import({
    SecurityConfig.class,
    AuthWebMvcConfig.class,
    PriceAlertServiceImpl.class,
    WatchlistAccessGuard.class,
    AccountAccessAuthorizer.class
})
class PriceAlertControllerTest {

    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;
    private static final long ENTRY = 7318826194416337412L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PriceAlertMapper alertMapper;

    @MockBean
    private WatchlistMapper watchlistMapper;

    @MockBean
    private AccountService accountService;

    @Test
    void createPriceAlert_owner_201() throws Exception {
        when(watchlistMapper.findWatchlistEntry(ENTRY)).thenReturn(entry(OWNER));
        doAnswer(invocation -> {
            invocation.<PriceAlertRow>getArgument(0).setAlertId(42L);
            return 1;
        }).when(alertMapper).insert(any());
        when(alertMapper.findForAccount(42L, OWNER)).thenReturn(alert(42L, PriceAlertStatus.ACTIVE));

        // the browser sends the 63-bit entry id as a string
        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"watchlistId": "7318826194416337412", "direction": "ABOVE", "threshold": 1500.00}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(42))
            .andExpect(jsonPath("$.watchlistId").value("7318826194416337412"))
            .andExpect(jsonPath("$.symbol").value("INFY.NS"))
            .andExpect(jsonPath("$.direction").value("ABOVE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.triggeredAt").doesNotExist());
    }

    @Test
    void createPriceAlert_anotherAccountsPath_403AndNothingReadOrWritten() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .with(authenticatedAs(OTHER_ACCOUNT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(alertMapper, watchlistMapper, accountService);
    }

    @Test
    void createPriceAlert_onAnotherAccountsWatchlistEntry_403() throws Exception {
        when(watchlistMapper.findWatchlistEntry(ENTRY)).thenReturn(entry(OTHER_ACCOUNT));

        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(alertMapper, never()).insert(any());
    }

    @Test
    void createPriceAlert_atTheCap_422AndNothingWritten() throws Exception {
        when(watchlistMapper.findWatchlistEntry(ENTRY)).thenReturn(entry(OWNER));
        when(alertMapper.countActiveByAccount(OWNER)).thenReturn(PriceAlertServiceImpl.DEFAULT_MAX_ACTIVE_ALERTS);

        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"))
            .andExpect(jsonPath("$.message").value("Price alert limit reached"));

        verify(alertMapper).lockAccountAlerts(OWNER);
        verify(alertMapper, never()).insert(any());
    }

    @Test
    void createPriceAlert_invalidBodies_422() throws Exception {
        for (String body : List.of(
            "{\"watchlistId\": \"7318826194416337412\", \"direction\": \"SIDEWAYS\", \"threshold\": 1500}",
            "{\"watchlistId\": \"7318826194416337412\", \"direction\": \"ABOVE\", \"threshold\": 0}",
            "{\"watchlistId\": \"7318826194416337412\", \"direction\": \"ABOVE\", \"threshold\": -5}",
            "{\"watchlistId\": \"7318826194416337412\", \"direction\": \"ABOVE\", \"threshold\": 1500.12345}",
            "{\"watchlistId\": \"7318826194416337412\", \"direction\": \"ABOVE\"}",
            "{\"direction\": \"BELOW\", \"threshold\": 1500}",
            "{\"watchlistId\": 0, \"direction\": \"BELOW\", \"threshold\": 1500}"
        )) {
            mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                    .with(authenticatedAs(OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(alertMapper, watchlistMapper);
    }

    @Test
    void createPriceAlert_sameActiveLevelAgain_409AndNothingWritten() throws Exception {
        when(watchlistMapper.findWatchlistEntry(ENTRY)).thenReturn(entry(OWNER));
        when(alertMapper.existsActive(OWNER, "INFY.NS", AlertDirection.ABOVE, new BigDecimal("1500.00"))).thenReturn(true);

        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("WTL-409"))
            .andExpect(jsonPath("$.message").value("Price alert already exists"));

        verify(alertMapper, never()).insert(any());
    }

    @Test
    void createPriceAlert_unknownWatchlistEntry_404() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("WTL-404"));
    }

    @Test
    void listPriceAlerts_owner_200WithStateAndLimit() throws Exception {
        when(alertMapper.findByAccount(OWNER)).thenReturn(List.of(
            alert(43L, PriceAlertStatus.ACTIVE),
            alert(42L, PriceAlertStatus.TRIGGERED)
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts", OWNER).with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxActiveAlerts").value(PriceAlertServiceImpl.DEFAULT_MAX_ACTIVE_ALERTS))
            .andExpect(jsonPath("$.activeAlerts").value(1))
            .andExpect(jsonPath("$.alerts", hasSize(2)))
            .andExpect(jsonPath("$.alerts[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.alerts[1].status").value("TRIGGERED"))
            .andExpect(jsonPath("$.alerts[1].triggerPrice").value(1502.35))
            .andExpect(jsonPath("$.alerts[1].triggeredAt").value("2026-10-07T09:30:00Z"))
            .andExpect(jsonPath("$.alerts[1].lastPrice").value(1510.0));
    }

    @Test
    void listPriceAlerts_anotherAccount_403AndNothingRead() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts", OWNER).with(authenticatedAs(OTHER_ACCOUNT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(alertMapper);
    }

    @Test
    void getPriceAlert_owner_200() throws Exception {
        when(alertMapper.findOwner(42L)).thenReturn(OWNER);
        when(alertMapper.findForAccount(42L, OWNER)).thenReturn(alert(42L, PriceAlertStatus.TRIGGERED));

        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts/{alertId}", OWNER, 42L).with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRIGGERED"));
    }

    @Test
    void getPriceAlert_belongingToAnotherAccount_403() throws Exception {
        when(alertMapper.findOwner(42L)).thenReturn(OTHER_ACCOUNT);

        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts/{alertId}", OWNER, 42L).with(authenticatedAs(OWNER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(alertMapper, never()).findForAccount(anyLong(), anyLong());
    }

    @Test
    void getPriceAlert_unknown_404() throws Exception {
        // Mockito would answer 0L for a Long; the SQL answers null for an id no account owns
        when(alertMapper.findOwner(42L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts/{alertId}", OWNER, 42L).with(authenticatedAs(OWNER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("WTL-404"))
            .andExpect(jsonPath("$.message").value("Price alert not found"));
    }

    @Test
    void deletePriceAlert_owner_204() throws Exception {
        when(alertMapper.findOwner(42L)).thenReturn(OWNER);
        when(alertMapper.deleteForAccount(42L, OWNER)).thenReturn(1);

        mockMvc.perform(delete("/api/v1/accounts/{id}/price-alerts/{alertId}", OWNER, 42L).with(authenticatedAs(OWNER)))
            .andExpect(status().isNoContent());
    }

    @Test
    void deletePriceAlert_belongingToAnotherAccount_403AndNothingDeleted() throws Exception {
        when(alertMapper.findOwner(42L)).thenReturn(OTHER_ACCOUNT);

        mockMvc.perform(delete("/api/v1/accounts/{id}/price-alerts/{alertId}", OWNER, 42L).with(authenticatedAs(OWNER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(alertMapper, never()).deleteForAccount(anyLong(), anyLong());
    }

    @Test
    void priceAlertRoutes_invalidPathIds_422() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts/{alertId}", OWNER, 0L).with(authenticatedAs(OWNER)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts", "abc").with(authenticatedAs(OWNER)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void priceAlertRoutes_withoutToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/price-alerts", OWNER))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
        mockMvc.perform(post("/api/v1/accounts/{id}/price-alerts", OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(alertMapper, watchlistMapper, accountService);
    }

    private static String validBody() {
        return "{\"watchlistId\": \"" + ENTRY + "\", \"direction\": \"ABOVE\", \"threshold\": 1500.00}";
    }

    private static WatchlistEntry entry(long owner) {
        return new WatchlistEntry(ENTRY, owner, "INFY.NS", "Infosys Limited");
    }

    private static PriceAlertResponse alert(long alertId, PriceAlertStatus status) {
        boolean triggered = status == PriceAlertStatus.TRIGGERED;
        return new PriceAlertResponse(alertId, ENTRY, "INFY.NS", "Infosys Limited", AlertDirection.ABOVE,
            new BigDecimal("1500.00"), status, OffsetDateTime.parse("2026-10-07T09:00:00Z"),
            triggered ? OffsetDateTime.parse("2026-10-07T09:30:00Z") : null,
            triggered ? new BigDecimal("1502.35") : null,
            new BigDecimal("1510.00"),
            "INR");
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token
            .claim("accountId", accountId)
            .claim("iss", "auth-service")
        );
    }
}
