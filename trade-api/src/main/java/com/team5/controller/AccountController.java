package com.team5.controller;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.service.AccountService;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
