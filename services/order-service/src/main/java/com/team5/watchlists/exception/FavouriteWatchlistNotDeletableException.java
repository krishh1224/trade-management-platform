package com.team5.watchlists.exception;

import com.team5.domain.exception.DomainException;

// The Favourite watchlist is the default every add without a group goes to; it cannot be deleted.
public class FavouriteWatchlistNotDeletableException extends DomainException {

    public FavouriteWatchlistNotDeletableException() {
        super("WTL-409", "The Favourite watchlist cannot be deleted");
    }
}
