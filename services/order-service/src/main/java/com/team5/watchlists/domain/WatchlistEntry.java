package com.team5.watchlists.domain;

// One watchlist row with its owner and instrument, looked up by id regardless of owner so the caller
// can tell "does not exist" (WTL-404) from "belongs to another account" (ACC-403).
public record WatchlistEntry(
    Long watchlistId,
    Long accountId,
    String symbol,
    String instrumentName
) {
}
