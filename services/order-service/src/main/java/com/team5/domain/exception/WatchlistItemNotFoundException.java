package com.team5.domain.exception;

public class WatchlistItemNotFoundException extends DomainException {

    public WatchlistItemNotFoundException() {
        super("WTL-404", "Watchlist item not found");
    }
}
