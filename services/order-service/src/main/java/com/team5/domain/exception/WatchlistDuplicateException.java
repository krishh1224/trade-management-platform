package com.team5.domain.exception;

public class WatchlistDuplicateException extends DomainException {

    public WatchlistDuplicateException() {
        super("WTL-409", "Instrument already in watchlist");
    }
}
