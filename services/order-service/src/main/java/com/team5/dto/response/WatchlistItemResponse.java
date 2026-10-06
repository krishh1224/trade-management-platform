package com.team5.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WatchlistItemResponse(
    @JsonSerialize(using = ToStringSerializer.class)
    Long watchlistId,
    @JsonSerialize(using = ToStringSerializer.class)
    Long watchlistGroupId,
    String watchlistName,
    Long instrumentId,
    String symbol,
    String instrumentName,
    OffsetDateTime addedAt,
    BigDecimal lastPrice,
    BigDecimal changePct
) {
}
