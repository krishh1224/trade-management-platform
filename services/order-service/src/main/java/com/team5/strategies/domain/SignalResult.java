package com.team5.strategies.domain;

import com.team5.domain.enums.OrderSide;

// The outcome of evaluating one strategy against its price series: a side to trade, or none, and why.
public record SignalResult(OrderSide side, String reason) {

    public static SignalResult buy(String reason) {
        return new SignalResult(OrderSide.BUY, reason);
    }

    public static SignalResult sell(String reason) {
        return new SignalResult(OrderSide.SELL, reason);
    }

    public static SignalResult none(String reason) {
        return new SignalResult(null, reason);
    }

    public boolean fired() {
        return side != null;
    }
}
