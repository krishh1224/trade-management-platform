package com.team5.watchlists.exception;

import com.team5.domain.exception.DomainException;

public class WatchlistDuplicateException extends DomainException {

    public WatchlistDuplicateException() {
        super("WTL-409", "Instrument already in watchlist");
    }
}
