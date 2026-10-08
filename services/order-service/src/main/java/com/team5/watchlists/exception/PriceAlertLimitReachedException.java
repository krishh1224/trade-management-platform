package com.team5.watchlists.exception;

import com.team5.domain.exception.DomainException;

// The account already holds the maximum number of ACTIVE alerts. The platform catalogue has no code
// for a quota, so this is the validation code with its own message (see the module README).
public class PriceAlertLimitReachedException extends DomainException {

    public PriceAlertLimitReachedException() {
        super("VAL-422", "Price alert limit reached");
    }
}
