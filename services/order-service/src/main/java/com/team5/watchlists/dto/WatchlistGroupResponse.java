package com.team5.watchlists.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.OffsetDateTime;

public record WatchlistGroupResponse(
    @JsonSerialize(using = ToStringSerializer.class)
    Long watchlistGroupId,
    String name,
    OffsetDateTime createdAt
) {
}
