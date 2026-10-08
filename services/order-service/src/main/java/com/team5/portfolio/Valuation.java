package com.team5.portfolio;

import com.team5.portfolio.dto.PricedPositionResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * An account's open holdings priced and converted into its base currency. The portfolio summary and the
 * profit and loss route both read their unrealised figures from here, so the two always agree.
 *
 * @param positionCount every open holding, including any that could not be priced
 */
public record Valuation(String baseCurrency, BigDecimal cash, int positionCount, List<ValuedHolding> holdings) {

    /**
     * One holding. The base-currency figures are rounded per holding, and null when the holding could not
     * be priced or its currency could not be converted; such a holding is left out of every total.
     */
    public record ValuedHolding(PricedPositionResponse position, BigDecimal marketValueBase,
                                BigDecimal costBasisBase) {

        public boolean counted() {
            return marketValueBase != null;
        }

        /** Unrealised profit and loss in the base currency, or null when the holding is not counted. */
        public BigDecimal unrealisedBase() {
            return counted() ? marketValueBase.subtract(costBasisBase) : null;
        }
    }

    /** True when at least one holding is left out of the totals. */
    public boolean partial() {
        return holdings.stream().anyMatch(holding -> !holding.counted());
    }

    public BigDecimal marketValue() {
        return holdings.stream().filter(ValuedHolding::counted).map(ValuedHolding::marketValueBase)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public BigDecimal costBasis() {
        return holdings.stream().filter(ValuedHolding::counted).map(ValuedHolding::costBasisBase)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public BigDecimal unrealisedPnl() {
        return marketValue().subtract(costBasis());
    }
}
