package com.team5.watchlists.domain;

// A row of the shared instruments reference table, read (never written) to validate a symbol.
public record WatchlistInstrument(
    Long instrumentId,
    String symbol,
    String name,
    Boolean tradable,
    String currency
) {
}
