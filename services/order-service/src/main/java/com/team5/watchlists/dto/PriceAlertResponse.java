package com.team5.watchlists.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.team5.notifications.api.AlertDirection;
import com.team5.watchlists.domain.PriceAlertStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceAlertResponse(
    Long alertId,
    @JsonSerialize(using = ToStringSerializer.class)
    Long watchlistId,
    String symbol,
    String instrumentName,
    AlertDirection direction,
    BigDecimal threshold,
    PriceAlertStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime triggeredAt,
    BigDecimal triggerPrice,
    BigDecimal lastPrice,
    // ISO 4217 code the instrument is quoted in; threshold, triggerPrice and lastPrice are in it
    String currency
) {
}
