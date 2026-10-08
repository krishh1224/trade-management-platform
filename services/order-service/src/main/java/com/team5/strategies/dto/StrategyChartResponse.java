package com.team5.strategies.dto;

import com.team5.domain.enums.OrderSide;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.StrategyType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * What a strategy's rule sees, for drawing: the stored prices for its symbol (oldest first), the
 * lines its rule compares at each price, and a marker wherever the rule fired.
 *
 * For MA_CROSSOVER {@code shortAverage} and {@code longAverage} are set; for BOLLINGER_BANDS
 * {@code middleBand}, {@code upperBand} and {@code lowerBand}. The others are null.
 */
public record StrategyChartResponse(
    long strategyId,
    String symbol,
    StrategyType type,
    int requiredPrices,
    List<Point> points,
    List<Marker> markers
) {

    public record Point(
        OffsetDateTime quotedAt,
        BigDecimal price,
        BigDecimal shortAverage,
        BigDecimal longAverage,
        BigDecimal middleBand,
        BigDecimal upperBand,
        BigDecimal lowerBand
    ) {
    }

    // one firing of the rule, on the point (by index) of the quote it fired on
    public record Marker(
        int pointIndex,
        OffsetDateTime quotedAt,
        BigDecimal quotePrice,
        OrderSide signal,
        ExecutionOutcome outcome,
        String orderId,
        String detail
    ) {
    }
}
