package com.team5.watchlists.web;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.watchlists.dto.CreatePriceAlertRequest;
import com.team5.watchlists.dto.PriceAlertListResponse;
import com.team5.watchlists.dto.PriceAlertResponse;
import com.team5.watchlists.service.PriceAlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// contracts/watchlists-api.yaml, tag "Price alerts". There is no route that triggers an alert or
// sends a notification: alerts trigger only from market-data.
@RestController
@Validated
@RequestMapping("/api/v1/accounts/{id}/price-alerts")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    public ResponseEntity<PriceAlertListResponse> listPriceAlerts(
        @PathVariable("id") @Min(1) long accountId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(priceAlertService.listAlerts(authenticatedAccountId, accountId));
    }

    @PostMapping
    public ResponseEntity<PriceAlertResponse> createPriceAlert(
        @PathVariable("id") @Min(1) long accountId,
        @Valid @RequestBody CreatePriceAlertRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(priceAlertService.createAlert(authenticatedAccountId, accountId, request));
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<PriceAlertResponse> getPriceAlert(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("alertId") @Min(1) long alertId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(priceAlertService.getAlert(authenticatedAccountId, accountId, alertId));
    }

    @DeleteMapping("/{alertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePriceAlert(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("alertId") @Min(1) long alertId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        priceAlertService.deleteAlert(authenticatedAccountId, accountId, alertId);
    }
}
