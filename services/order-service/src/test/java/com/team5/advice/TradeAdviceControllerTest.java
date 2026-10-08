package com.team5.advice;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TradeAdviceController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class})
class TradeAdviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeAdviceService tradeAdviceService;

    @Test
    void returnsSignalsWithReasoning() throws Exception {
        when(tradeAdviceService.getSignals(101L, 101L)).thenReturn(new TradeAdviceResponse(
            101L,
            Instant.parse("2026-10-07T12:30:00Z"),
            List.of(new AdviceSignalResponse(
                "INFY.NS",
                null,
                "HOLDING",
                "BUY",
                List.of("Unrealised P&L is +14.00% versus average cost."),
                new java.math.BigDecimal("1710.00"),
                null,
                new java.math.BigDecimal("14.00"),
                "INR",
                Instant.parse("2026-10-07T12:25:00Z"),
                false
            )),
            List.of()
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/advisor/signals", 101L).with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(101))
            .andExpect(jsonPath("$.signals[0].symbol").value("INFY.NS"))
            .andExpect(jsonPath("$.signals[0].signal").value("BUY"))
            .andExpect(jsonPath("$.signals[0].reasoning[0]").value("Unrealised P&L is +14.00% versus average cost."));
    }

    @Test
    void unauthorisedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/advisor/signals", 101L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token
            .claim("accountId", accountId)
            .claim("iss", "auth-service")
        );
    }
}