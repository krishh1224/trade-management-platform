package com.team5.watchlists.exception;

import com.team5.domain.exception.DomainException;

public class WatchlistItemNotFoundException extends DomainException {

    public WatchlistItemNotFoundException() {
        super("WTL-404", "Watchlist item not found");
    }
}
