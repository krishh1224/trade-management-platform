package com.team5.portfolio.candles;

import java.util.List;

/** Everything Fauxnance returned for one symbol, oldest first. Ranges are sliced from this. */
public record CandleSeries(String symbol, String currency, List<Candle> candles) {
}
