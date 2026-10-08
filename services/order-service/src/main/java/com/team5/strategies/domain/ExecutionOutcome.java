package com.team5.strategies.domain;

// What happened when a strategy's rule fired on a quote. Only PLACED means an order exists.
public enum ExecutionOutcome {
    // the Trade API accepted the order (or had already accepted it under the same idempotency key)
    PLACED,
    // the Trade API refused the order, or could not be reached; counts towards halting the strategy
    FAILED,
    // the strategy was no longer ENABLED when the decision was taken
    SKIPPED_DISABLED,
    // the order would have taken the strategy's spending past max_spend
    SKIPPED_SPEND_LIMIT,
    // the order would have taken the account's position in the symbol past max_position
    SKIPPED_POSITION_LIMIT,
    // a SELL with nothing left to sell
    SKIPPED_NO_HOLDING
}
