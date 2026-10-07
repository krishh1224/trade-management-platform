package com.team5.notifications.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// What watchlists hands over when a threshold is crossed. Structured fields only: the
// notifications module writes the wording, so no caller can push free text to a customer.
public record PriceAlert(
    UUID alertId,              // idempotency key; the same alertId twice delivers once
    long accountId,            // the account that owns the watchlist entry
    String symbol,
    BigDecimal threshold,
    BigDecimal triggerPrice,
    AlertDirection direction,
    OffsetDateTime triggeredAt
) {
}
