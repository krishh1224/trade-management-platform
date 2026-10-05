package com.neueda.learning.domain;

import java.math.BigDecimal;

/**
 * Represents a price quote from Fauxnance API.
 * Contains bid/ask prices with BigDecimal precision.
 */
public class Quote {
    private final String symbol;
    private final BigDecimal bid;
    private final BigDecimal ask;
    private final BigDecimal changePct;

    public Quote(String symbol, BigDecimal bid, BigDecimal ask) {
        this(symbol, bid, ask, null);
    }

    public Quote(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal changePct) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (bid == null) {
            throw new IllegalArgumentException("bid must not be null");
        }
        if (ask == null) {
            throw new IllegalArgumentException("ask must not be null");
        }
        this.symbol = symbol;
        this.bid = bid;
        this.ask = ask;
        this.changePct = changePct;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public BigDecimal getChangePct() {
        return changePct;
    }

    @Override
    public String toString() {
        return "Quote{" +
                "symbol='" + symbol + '\'' +
                ", bid=" + bid +
                ", ask=" + ask +
            ", changePct=" + changePct +
                '}';
    }
}

