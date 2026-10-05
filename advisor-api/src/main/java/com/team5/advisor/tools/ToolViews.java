package com.team5.advisor.tools;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What each tool returns to the model (serialised to JSON). Field names are written for the model:
 * every price carries its age, and every converted amount names the rate and where it came from.
 */
public final class ToolViews {

    private ToolViews() {
    }

    public record PortfolioView(
        String baseCurrency,
        BigDecimal cashBalance,
        List<HoldingView> holdings,
        BigDecimal holdingsValueInBaseCurrency,
        BigDecimal totalValueInBaseCurrency,
        List<String> notes
    ) {
    }

    public record HoldingView(
        String symbol,
        String name,
        String exchange,
        String currency,
        long quantity,
        BigDecimal averageCost,
        BigDecimal lastPrice,
        BigDecimal changePercentToday,
        LocalDateTime priceUpdatedAt,
        Long priceAgeMinutes,
        BigDecimal marketValue,
        BigDecimal unrealisedGainPercent,
        BigDecimal fxRateToBaseCurrency,
        LocalDateTime fxRateAsOf,
        BigDecimal marketValueInBaseCurrency,
        BigDecimal shareOfHoldingsPercent
    ) {
    }

    public record QuoteView(
        String symbol,
        String name,
        String exchange,
        String currency,
        boolean priceAvailable,
        BigDecimal lastPrice,
        BigDecimal changePercentToday,
        LocalDateTime priceUpdatedAt,
        Long priceAgeMinutes
    ) {
    }

    public record InstrumentView(
        String symbol,
        String name,
        String exchange,
        String currency,
        String assetClass,
        boolean tradable,
        BigDecimal lastPrice,
        LocalDateTime priceUpdatedAt,
        Long priceAgeMinutes
    ) {
    }

    public record FxRateView(
        String fromCurrency,
        String toCurrency,
        boolean available,
        BigDecimal rate,
        LocalDateTime asOf,
        String source
    ) {
    }

    public record OrderView(
        String symbol,
        String side,
        long quantity,
        String status,
        String instrumentCurrency,
        BigDecimal limitPrice,
        BigDecimal executedPrice,
        BigDecimal settledAmount,
        String settledCurrency,
        String rejectionReason,
        LocalDateTime createdOn
    ) {
    }
}
