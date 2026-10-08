package com.team5.strategies.exception;

import com.team5.domain.exception.DomainException;

// No strategy with that id exists for any account.
public class StrategyNotFoundException extends DomainException {

    public StrategyNotFoundException() {
        super("STR-404", "Strategy not found");
    }
}
