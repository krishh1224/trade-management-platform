package com.team5.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// field order must match the getBalance query in AccountQueryMapper.xml
public record BalanceResponse(
    Long accountId,
    BigDecimal cashBalance,
    BigDecimal heldForOrders,
    BigDecimal availableToWithdraw,
    String currency,
    OffsetDateTime asOf
) {
}
