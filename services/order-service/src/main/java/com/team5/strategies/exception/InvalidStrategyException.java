package com.team5.strategies.exception;

import com.team5.domain.exception.DomainException;

// A strategy whose parameters do not fit together (windows, band width, limits), or one too many for
// the account. The validation code, with a message saying which rule.
public class InvalidStrategyException extends DomainException {

    public InvalidStrategyException(String message) {
        super("VAL-422", message);
    }
}
