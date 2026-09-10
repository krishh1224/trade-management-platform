package com.team5.tradeapi.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.controller.AccountController;
import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.JwtTokenVerifier;
import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    private static final String AUTHENTICATED_ACCOUNT_ID = "authenticatedAccountId";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private AccountAccessAuthorizer accountAccessAuthorizer;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @Test
    void getAccount_shouldReturnAccountResponse() throws Exception {
        when(accountService.getAccount(101L)).thenReturn(new AccountResponse(
            101L,
            "ACC-000101",
            "Priya Menon",
            BigDecimal.valueOf(95000.00),
            AccountStatus.ACTIVE,
            7,
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}", 101L)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.accountId").value("ACC-000101"))
            .andExpect(jsonPath("$.holderName").value("Priya Menon"))
            .andExpect(jsonPath("$.cashBalance").value(95000.0))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.version").value(7))
            .andExpect(jsonPath("$.lastUpdated").value("2026-09-09T14:00:00Z"));
    }

    @Test
    void getBalance_shouldReturnBalanceResponse() throws Exception {
        when(accountService.getBalance(101L)).thenReturn(new BalanceResponse(
            101L,
            BigDecimal.valueOf(95000.00),
            "INR",
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", 101L)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(101))
            .andExpect(jsonPath("$.cashBalance").value(95000.0))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.asOf").value("2026-09-09T14:00:00Z"));
    }

    @Test
    void getPositions_shouldReturnPositionArray() throws Exception {
        when(accountService.getPositions(101L)).thenReturn(List.of(
            new PositionResponse(101L, "INFY.NS", 100, BigDecimal.valueOf(1450.00))
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/positions", 101L)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].accountId").value(101))
            .andExpect(jsonPath("$[0].symbol").value("INFY.NS"))
            .andExpect(jsonPath("$[0].quantity").value(100))
            .andExpect(jsonPath("$[0].averageCost").value(1450.0));
    }

    @Test
    void getOrders_shouldAcceptOptionalQueryParameters() throws Exception {
        when(accountService.getOrders(
            eq(101L),
            eq(OrderStatus.FILLED),
            eq(OffsetDateTime.parse("2026-09-01T00:00:00Z")),
            eq(OffsetDateTime.parse("2026-09-30T23:59:59Z"))
        )).thenReturn(List.of(
            new OrderHistoryEntry(
                "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
                101L,
                "INFY.NS",
                OrderSide.BUY,
                10,
                BigDecimal.valueOf(1500.00),
                BigDecimal.valueOf(1499.75),
                OrderStatus.FILLED,
                "order-123456",
                OffsetDateTime.parse("2026-09-09T14:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/orders", 101L)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L)
                .queryParam("status", "FILLED")
                .queryParam("from", "2026-09-01T00:00:00Z")
                .queryParam("to", "2026-09-30T23:59:59Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value("ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e"))
            .andExpect(jsonPath("$[0].status").value("FILLED"));
    }

    @Test
    void getOrders_shouldWorkWithoutQueryParameters() throws Exception {
        when(accountService.getOrders(eq(101L), isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/{id}/orders", 101L)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
