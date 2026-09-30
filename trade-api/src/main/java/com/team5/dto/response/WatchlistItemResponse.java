package com.team5.dto.response;

import java.time.OffsetDateTime;

public record WatchlistItemResponse(
    Long watchlistId,
    Long instrumentId,
    String symbol,
    String instrumentName,
    OffsetDateTime addedAt
) {
}
