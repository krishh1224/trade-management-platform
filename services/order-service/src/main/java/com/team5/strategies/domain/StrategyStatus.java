package com.team5.strategies.domain;

public enum StrategyStatus {
    // evaluated against every quote for its symbol, may place orders
    ENABLED,
    // switched off by the customer
    DISABLED,
    // switched off by the module after too many failed orders in a row; the customer re-enables it
    HALTED
}
