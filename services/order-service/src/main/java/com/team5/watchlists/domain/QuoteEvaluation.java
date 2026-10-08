package com.team5.watchlists.domain;

// What one market-data quote did, for the consumer's log and for tests.
public record QuoteEvaluation(Outcome outcome, int triggered) {

    public enum Outcome {
        // the first price seen for the symbol: stored, nothing to cross from
        SEEDED,
        // not newer than the stored price (a redelivery or a replay): ignored, nothing changed
        STALE,
        // stored as the new price and evaluated against the symbol's active alerts
        EVALUATED
    }

    public static QuoteEvaluation seeded() {
        return new QuoteEvaluation(Outcome.SEEDED, 0);
    }

    public static QuoteEvaluation stale() {
        return new QuoteEvaluation(Outcome.STALE, 0);
    }

    public static QuoteEvaluation evaluated(int triggered) {
        return new QuoteEvaluation(Outcome.EVALUATED, triggered);
    }
}
