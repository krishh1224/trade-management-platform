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

    public Quote(String symbol, BigDecimal bid, BigDecimal ask) {
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

    @Override
    public String toString() {
        return "Quote{" +
                "symbol='" + symbol + '\'' +
                ", bid=" + bid +
                ", ask=" + ask +
                '}';
    }
}

