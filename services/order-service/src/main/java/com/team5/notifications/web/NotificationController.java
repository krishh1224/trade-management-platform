package com.team5.notifications.web;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Contract: contracts/notifications-api.yaml. Every route compares the token's accountId claim
// with {id} in the service before anything is read.
@RestController
@Validated
@RequestMapping("/api/v1/accounts/{id}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> listNotifications(
        @PathVariable("id") @Min(1) Long id,
        @RequestParam(value = "status", required = false) NotificationStatus status,
        @RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(100) int limit,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(notificationService.listNotifications(authenticatedAccountId, id, status, limit));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> getNotification(
        @PathVariable("id") @Min(1) Long id,
        @PathVariable("notificationId") @Min(1) Long notificationId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(notificationService.getNotification(authenticatedAccountId, id, notificationId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
        @PathVariable("id") @Min(1) Long id,
        @PathVariable("notificationId") @Min(1) Long notificationId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(notificationService.markRead(authenticatedAccountId, id, notificationId));
    }
}
