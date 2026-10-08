package com.team5.strategies.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// One QUOTE from market-data, reduced to what strategy evaluation needs. price is the quote's price, or
// the bid/ask midpoint when the producer sends only bid and ask (the executor does). bid and ask are
// kept, when present, to set a limit price the executor will fill.
public record StrategyQuote(
    UUID eventId,
    String symbol,
    BigDecimal price,
    BigDecimal bid,
    BigDecimal ask,
    OffsetDateTime quotedAt
) {
}
