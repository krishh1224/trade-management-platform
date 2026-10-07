package com.team5.watchlists.dto;

import java.util.List;

// The account's alerts with the active-alert limit, so the UI can say "3 of 20" before a create fails.
public record PriceAlertListResponse(
    int maxActiveAlerts,
    int activeAlerts,
    List<PriceAlertResponse> alerts
) {
}
