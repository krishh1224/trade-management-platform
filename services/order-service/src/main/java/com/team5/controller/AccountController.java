package com.team5.controller;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.request.CashMovementRequest;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.CashTransactionResponse;
import com.team5.dto.response.ClientProfileResponse;
import com.team5.dto.response.InstrumentOptionResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
        @PathVariable("id") @Min(1) Long id,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getAccount(authenticatedAccountId, id));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ClientProfileResponse> getClientProfile(
        @PathVariable("id") @Min(1) Long id,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getClientProfile(authenticatedAccountId, id));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
        @PathVariable("id") @Min(1) Long id,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getBalance(authenticatedAccountId, id));
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(
        @PathVariable("id") @Min(1) Long id,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getPositions(authenticatedAccountId, id));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
        @PathVariable("id") @Min(1) Long id,
        @RequestParam(value = "status", required = false) OrderStatus status,
        @RequestParam(value = "from", required = false) OffsetDateTime from,
        @RequestParam(value = "to", required = false) OffsetDateTime to,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getOrders(authenticatedAccountId, id, status, from, to));
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<CashTransactionResponse> deposit(
        @PathVariable("id") @Min(1) Long id,
        @Valid @RequestBody CashMovementRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountService.deposit(authenticatedAccountId, id, request));
    }

    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<CashTransactionResponse> withdraw(
        @PathVariable("id") @Min(1) Long id,
        @Valid @RequestBody CashMovementRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountService.withdraw(authenticatedAccountId, id, request));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<CashTransactionResponse>> getCashTransactions(
        @PathVariable("id") @Min(1) Long id,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getCashTransactions(authenticatedAccountId, id, limit));
    }

    @GetMapping("/{id}/instruments")
    public ResponseEntity<List<InstrumentOptionResponse>> getInstruments(
        @PathVariable("id") @Min(1) Long id,
        @RequestParam(value = "prefix", required = false) String prefix,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(accountService.getInstruments(authenticatedAccountId, id, prefix));
    }
}
