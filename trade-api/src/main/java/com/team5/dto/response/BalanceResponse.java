package com.team5.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BalanceResponse(
    Long accountId,
    BigDecimal cashBalance,
    String currency,
    OffsetDateTime asOf
) {
}
