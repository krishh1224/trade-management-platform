package com.team5.strategies.domain;

public enum StrategyType {
    // BUY when the short moving average crosses above the long one, SELL when it crosses below
    MA_CROSSOVER,
    // BUY when the price crosses below the lower band, SELL when it crosses above the upper band
    BOLLINGER_BANDS
}
