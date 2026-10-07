package com.team5.watchlists.exception;

import com.team5.domain.exception.DomainException;

// No alert with that id exists for any account. The module's own not-found code, worded for an alert.
public class PriceAlertNotFoundException extends DomainException {

    public PriceAlertNotFoundException() {
        super("WTL-404", "Price alert not found");
    }
}
