package com.team5.tradeapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.controller.AccountController;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.domain.enums.TransactionType;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.CashMovementConflictException;
import com.team5.domain.exception.WithdrawalExceedsAvailableException;
import com.team5.dto.request.CashMovementRequest;
import com.team5.dto.request.UpdatePreferencesRequest;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.CashTransactionResponse;
import com.team5.dto.response.CustomerPreferencesResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.notifications.service.NotificationService;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private NotificationService notificationService;

    @Test
    void getAccount_shouldReturnAccountResponse() throws Exception {
        when(accountService.getAccount(101L, 101L)).thenReturn(new AccountResponse(
            101L,
            "ACC-000101",
            "Priya Menon",
            BigDecimal.valueOf(95000.00),
            AccountStatus.ACTIVE,
            7,
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}", 101L)
            .with(authenticatedAs(101L)))
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
        when(accountService.getBalance(101L, 101L)).thenReturn(new BalanceResponse(
            101L,
            BigDecimal.valueOf(95000.00),
            BigDecimal.valueOf(5000.00),
            BigDecimal.valueOf(90000.00),
            "INR",
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", 101L)
            .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(101))
            .andExpect(jsonPath("$.cashBalance").value(95000.0))
            .andExpect(jsonPath("$.heldForOrders").value(5000.0))
            .andExpect(jsonPath("$.availableToWithdraw").value(90000.0))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.asOf").value("2026-09-09T14:00:00Z"));
    }

    @Test
    void getPositions_shouldReturnPositionArray() throws Exception {
        when(accountService.getPositions(101L, 101L)).thenReturn(List.of(
            new PositionResponse(101L, "INFY.NS", 100, BigDecimal.valueOf(1450.00))
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/positions", 101L)
            .with(authenticatedAs(101L)))
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
                OffsetDateTime.parse("2026-09-09T14:00:00Z"),
                BigDecimal.valueOf(14997.50)
            )
        ));

        mockMvc.perform(get("/api/v1/accounts/{id}/orders", 101L)
            .with(authenticatedAs(101L))
                .queryParam("status", "FILLED")
                .queryParam("from", "2026-09-01T00:00:00Z")
                .queryParam("to", "2026-09-30T23:59:59Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value("ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e"))
            .andExpect(jsonPath("$[0].status").value("FILLED"))
            .andExpect(jsonPath("$[0].settlementAmount").value(14997.50));
    }

    @Test
    void getOrders_shouldWorkWithoutQueryParameters() throws Exception {
        when(accountService.getOrders(eq(101L), eq(101L), isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/{id}/orders", 101L)
            .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void exportOrdersByEmail_shouldQueueNotificationAndReturnAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/orders/export-email", 101L)
                .with(authenticatedAs(101L)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value("Order history PDF will be sent to your registered email."));

        verify(notificationService).queueOrderHistoryExport(101L, 101L);
    }

    @Test
    void getAccount_shouldReturn404ForUnknownAccount() throws Exception {
        when(accountService.getAccount(999L, 999L)).thenThrow(new AccountNotFoundException());

        mockMvc.perform(get("/api/v1/accounts/{id}", 999L)
            .with(authenticatedAs(999L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACC-404"))
            .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void getBalance_shouldReturn403WhenAccountIsInactive() throws Exception {
        when(accountService.getBalance(101L, 101L)).thenThrow(new AccountNotActiveException());

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", 101L)
            .with(authenticatedAs(101L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"))
            .andExpect(jsonPath("$.message").value("Account not active"));
    }

    @Test
    void deposit_shouldReturn201WithLedgerEntry() throws Exception {
        when(accountService.deposit(eq(101L), eq(101L), any(CashMovementRequest.class))).thenReturn(
            cashTransaction(TransactionType.DEPOSIT, "50000.00", "200000.00", "250000.00"));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposits", 101L)
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":50000.00,\"idempotencyKey\":\"deposit-key-1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("DEPOSIT"))
            .andExpect(jsonPath("$.amount").value(50000.0))
            .andExpect(jsonPath("$.balanceBefore").value(200000.0))
            .andExpect(jsonPath("$.balanceAfter").value(250000.0))
            .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void withdraw_shouldReturn400WhenAmountExceedsAvailable() throws Exception {
        when(accountService.withdraw(eq(101L), eq(101L), any(CashMovementRequest.class)))
            .thenThrow(new WithdrawalExceedsAvailableException());

        mockMvc.perform(post("/api/v1/accounts/{id}/withdrawals", 101L)
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":999999.00,\"idempotencyKey\":\"withdraw-key-1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("CSH-400"));
    }

    @Test
    void deposit_shouldReturn409WhenIdempotencyKeyWasUsed() throws Exception {
        when(accountService.deposit(eq(101L), eq(101L), any(CashMovementRequest.class)))
            .thenThrow(new CashMovementConflictException("Duplicate cash movement"));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposits", 101L)
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10.00,\"idempotencyKey\":\"used-key-1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CSH-409"));
    }

    @Test
    void deposit_shouldReturn422ForInvalidAmounts() throws Exception {
        for (String amount : new String[] {"0", "-5", "10.005"}) {
            mockMvc.perform(post("/api/v1/accounts/{id}/deposits", 101L)
                    .with(authenticatedAs(101L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":" + amount + ",\"idempotencyKey\":\"deposit-key-2\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(accountService);
    }

    @Test
    void getCashTransactions_shouldUseDefaultLimit() throws Exception {
        when(accountService.getCashTransactions(101L, 101L, 20)).thenReturn(List.of(
            cashTransaction(TransactionType.WITHDRAWAL, "20000.00", "270000.00", "250000.00")));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", 101L)
                .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("WITHDRAWAL"))
            .andExpect(jsonPath("$[0].balanceAfter").value(250000.0));
    }

    @Test
    void getCashTransactions_shouldReturn422WhenLimitIsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", 101L)
                .with(authenticatedAs(101L))
                .queryParam("limit", "101"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void preflightFromTheAngularDevServer_shouldBeAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/accounts/{id}/deposits", 101L)
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        verifyNoInteractions(accountService);
    }

    @Test
    void preflightFromAnUnlistedOrigin_shouldBeRefused() throws Exception {
        mockMvc.perform(options("/api/v1/accounts/{id}/deposits", 101L)
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void getPreferences_shouldReturnCurrentPreferences() throws Exception {
        when(accountService.getPreferences(101L, 101L)).thenReturn(
            new CustomerPreferencesResponse("INR", "DARK", "SMS")
        );

        mockMvc.perform(get("/api/v1/accounts/{id}/preferences", 101L)
                .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.theme").value("DARK"))
            .andExpect(jsonPath("$.alertChannel").value("SMS"));
    }

    @Test
    void updatePreferences_shouldPersistAndReturnUpdatedPreferences() throws Exception {
        when(accountService.updatePreferences(eq(101L), eq(101L), any(UpdatePreferencesRequest.class))).thenReturn(
            new CustomerPreferencesResponse("USD", "LIGHT", "EMAIL")
        );

        mockMvc.perform(put("/api/v1/accounts/{id}/preferences", 101L)
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"USD\",\"theme\":\"LIGHT\",\"alertChannel\":\"EMAIL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.theme").value("LIGHT"))
            .andExpect(jsonPath("$.alertChannel").value("EMAIL"));
    }

    @Test
    void updatePreferences_shouldReturn422ForInvalidPayload() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/preferences", 101L)
                .with(authenticatedAs(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"EUR\",\"theme\":\"LIGHT\",\"alertChannel\":\"PAGER\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    private static CashTransactionResponse cashTransaction(
        TransactionType type, String amount, String before, String after
    ) {
        return new CashTransactionResponse(
            4821937465L,
            101L,
            type,
            new BigDecimal(amount),
            "INR",
            new BigDecimal(before),
            new BigDecimal(after),
            OffsetDateTime.parse("2026-10-02T10:14:22Z")
        );
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token.claim("accountId", accountId));
    }
}
