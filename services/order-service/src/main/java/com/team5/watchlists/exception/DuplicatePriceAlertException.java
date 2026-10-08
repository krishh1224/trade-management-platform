package com.team5.watchlists.exception;

import com.team5.domain.exception.DomainException;

// The account already has an ACTIVE alert on this symbol, in this direction, at this threshold. A second
// one would fire on the same quote and send the same message twice.
public class DuplicatePriceAlertException extends DomainException {

    public DuplicatePriceAlertException() {
        super("WTL-409", "Price alert already exists");
    }
}
