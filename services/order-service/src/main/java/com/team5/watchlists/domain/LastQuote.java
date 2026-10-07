package com.team5.watchlists.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// The last price this module consumed for a symbol: what a crossing is measured from.
public record LastQuote(
    BigDecimal lastPrice,
    OffsetDateTime quotedAt
) {
}
