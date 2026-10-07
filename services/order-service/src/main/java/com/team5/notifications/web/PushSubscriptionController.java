package com.team5.notifications.web;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.notifications.service.PushSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Contract: contracts/notifications-api.yaml. The browser registers itself here so the PUSH channel
// can reach it. Every route compares the token's accountId claim with {id}.
@RestController
@Validated
@RequestMapping("/api/v1/accounts/{id}/push-subscription")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    public PushSubscriptionController(PushSubscriptionService pushSubscriptionService) {
        this.pushSubscriptionService = pushSubscriptionService;
    }

    @GetMapping("/config")
    public ResponseEntity<PushConfigResponse> config(
        @PathVariable("id") @Min(1) Long id,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(pushSubscriptionService.config(authenticatedAccountId, id));
    }

    @PutMapping
    public ResponseEntity<Void> subscribe(
        @PathVariable("id") @Min(1) Long id,
        @Valid @RequestBody PushSubscriptionRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        pushSubscriptionService.subscribe(authenticatedAccountId, id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(
        @PathVariable("id") @Min(1) Long id,
        @RequestParam("endpoint") @NotBlank @Size(max = 1000) String endpoint,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        pushSubscriptionService.unsubscribe(authenticatedAccountId, id, endpoint);
        return ResponseEntity.noContent().build();
    }
}
