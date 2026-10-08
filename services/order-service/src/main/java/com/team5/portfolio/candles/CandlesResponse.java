package com.team5.portfolio.candles;

import java.time.Instant;
import java.util.List;

/**
 * @param stale true when the provider could not be reached and an older copy was served
 * @param asOf  when this series was fetched from the provider
 */
public record CandlesResponse(
    String symbol,
    String currency,
    String interval,
    String range,
    boolean stale,
    Instant asOf,
    List<Candle> candles
) {
}
