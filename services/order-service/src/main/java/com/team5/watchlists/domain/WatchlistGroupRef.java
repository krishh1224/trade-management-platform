package com.team5.watchlists.domain;

// A watchlist group with its owner, looked up by id regardless of owner so the caller can tell
// "does not exist" (WTL-404) from "belongs to another account" (ACC-403).
public record WatchlistGroupRef(
    Long watchlistGroupId,
    Long accountId,
    String name
) {
}
