package com.team5.portfolio.exception;

import com.team5.domain.exception.DomainException;

/** No price could be obtained for any held instrument. */
public class PricingUnavailableException extends DomainException {

    public PricingUnavailableException() {
        super("MKT-503", "Pricing unavailable");
    }
}
