package com.team5.advisor.tools;

import com.team5.advisor.data.AdvisorRepository.FxRow;
import com.team5.advisor.data.AdvisorRepository.HoldingRow;
import com.team5.advisor.tools.ToolViews.HoldingView;
import com.team5.advisor.tools.ToolViews.PortfolioView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Values a portfolio in the account's base currency. Pure: no database, no HTTP.
 *
 * A holding is valued at its last snapshot price. One quoted in another currency is converted at the
 * most recent settled FX rate for that currency; with no price or no rate it is listed but left out
 * of the totals, and a note says why so the model never presents a partial total as complete.
 */
public final class PortfolioValuer {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PortfolioValuer() {
    }

    public static PortfolioView value(
        String baseCurrency,
        BigDecimal cashBalance,
        List<HoldingRow> holdings,
        BiFunction<String, String, Optional<FxRow>> fxLookup
    ) {
        Map<String, Optional<FxRow>> fxCache = new HashMap<>();
        List<String> notes = new ArrayList<>();
        List<BigDecimal> baseValues = new ArrayList<>();
        BigDecimal holdingsTotal = BigDecimal.ZERO;

        for (HoldingRow row : holdings) {
            BigDecimal marketValue = row.lastPrice() == null
                ? null
                : money(row.lastPrice().multiply(BigDecimal.valueOf(row.quantity())));

            BigDecimal fxRate = null;
            FxRow fx = null;
            if (row.currency().equalsIgnoreCase(baseCurrency)) {
                fxRate = BigDecimal.ONE;
            } else {
                fx = fxCache.computeIfAbsent(row.currency(), c -> fxLookup.apply(c, baseCurrency)).orElse(null);
                fxRate = fx == null ? null : fx.rate();
            }

            BigDecimal baseValue = marketValue == null || fxRate == null ? null : money(marketValue.multiply(fxRate));
            if (marketValue == null) {
                notes.add(row.symbol() + " has no recent price, so it is not included in the totals.");
            } else if (fxRate == null) {
                notes.add(row.symbol() + " is quoted in " + row.currency() + " and no " + row.currency() + "/"
                    + baseCurrency + " rate has been recorded yet, so it is not included in the totals.");
            } else {
                holdingsTotal = holdingsTotal.add(baseValue);
            }
            baseValues.add(baseValue);
        }

        List<HoldingView> views = new ArrayList<>();
        for (int i = 0; i < holdings.size(); i++) {
            HoldingRow row = holdings.get(i);
            BigDecimal baseValue = baseValues.get(i);
            BigDecimal fxRate = row.currency().equalsIgnoreCase(baseCurrency)
                ? BigDecimal.ONE
                : fxCache.getOrDefault(row.currency(), Optional.empty()).map(FxRow::rate).orElse(null);
            views.add(new HoldingView(
                row.symbol(),
                row.name(),
                row.exchange(),
                row.currency(),
                row.quantity(),
                row.avgCost(),
                row.lastPrice(),
                row.changePct(),
                row.priceUpdatedAt(),
                row.priceAgeMinutes(),
                row.lastPrice() == null ? null : money(row.lastPrice().multiply(BigDecimal.valueOf(row.quantity()))),
                gainPercent(row.avgCost(), row.lastPrice()),
                fxRate,
                row.currency().equalsIgnoreCase(baseCurrency)
                    ? null
                    : fxCache.getOrDefault(row.currency(), Optional.empty()).map(FxRow::asOf).orElse(null),
                baseValue,
                baseValue == null || holdingsTotal.signum() == 0
                    ? null
                    : baseValue.multiply(HUNDRED).divide(holdingsTotal, MONEY_SCALE, RoundingMode.HALF_UP)
            ));
        }

        if (fxCache.values().stream().anyMatch(Optional::isPresent)) {
            notes.add("Foreign-currency values use the rate from the most recent settled order in that currency, "
                + "so they are approximate.");
        }

        BigDecimal cash = cashBalance == null ? BigDecimal.ZERO : cashBalance;
        return new PortfolioView(
            baseCurrency,
            money(cash),
            views,
            money(holdingsTotal),
            money(cash.add(holdingsTotal)),
            notes
        );
    }

    /** Unrealised gain of the last price over the average cost, in percent (same currency for both). */
    static BigDecimal gainPercent(BigDecimal averageCost, BigDecimal lastPrice) {
        if (averageCost == null || lastPrice == null || averageCost.signum() == 0) {
            return null;
        }
        return lastPrice.subtract(averageCost)
            .multiply(HUNDRED)
            .divide(averageCost, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
