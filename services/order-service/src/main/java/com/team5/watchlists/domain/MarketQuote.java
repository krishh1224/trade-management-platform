package com.team5.watchlists.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// One QUOTE from market-data, reduced to what alert evaluation needs. price is the quote's price, or
// the bid/ask midpoint when the producer sends only bid and ask (the executor does).
public record MarketQuote(
    UUID eventId,
    String symbol,
    BigDecimal price,
    OffsetDateTime quotedAt
) {
}
