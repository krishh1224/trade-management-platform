package com.team5.strategies;

import com.team5.strategies.config.StrategyProperties;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.domain.StrategyType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class StrategyFixtures {

    public static final long OWNER = 101L;
    public static final String SYMBOL = "INFY.NS";
    public static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-08T09:00:00Z");

    private StrategyFixtures() {
    }

    public static StrategyProperties defaultProperties() {
        return new StrategyProperties(null, null, null, null, null, null, null);
    }

    // SMA(2) / SMA(4), 10 shares per order, 100 000 to spend, at most 50 held
    public static StrategyRow maCrossover(long strategyId) {
        StrategyRow row = new StrategyRow();
        row.setStrategyId(strategyId);
        row.setAccountId(OWNER);
        row.setName("INFY trend");
        row.setSymbol(SYMBOL);
        row.setStrategyType(StrategyType.MA_CROSSOVER);
        row.setShortWindow(2);
        row.setLongWindow(4);
        row.setQuantity(10);
        row.setMaxSpend(new BigDecimal("100000.00"));
        row.setMaxPosition(50L);
        row.setMaxConsecutiveFailures(3);
        row.setStatus(StrategyStatus.ENABLED);
        row.setSpent(BigDecimal.ZERO.setScale(2));
        row.setOrdersPlaced(0);
        row.setConsecutiveFailures(0);
        row.setCreatedAt(T0);
        row.setUpdatedAt(T0);
        return row;
    }

    public static StrategyRow bollinger(long strategyId) {
        StrategyRow row = maCrossover(strategyId);
        row.setName("INFY bands");
        row.setStrategyType(StrategyType.BOLLINGER_BANDS);
        row.setShortWindow(null);
        row.setLongWindow(null);
        row.setBandWindow(3);
        row.setBandWidth(BigDecimal.ONE);
        return row;
    }

    public static StrategyQuote quote(String price) {
        return quote(UUID.randomUUID(), price, T0);
    }

    // bid and ask 0.50 either side of the price, as the executor's poller publishes them
    public static StrategyQuote quote(UUID eventId, String price, OffsetDateTime at) {
        BigDecimal mid = new BigDecimal(price);
        return new StrategyQuote(eventId, SYMBOL, mid, mid.subtract(new BigDecimal("0.50")),
            mid.add(new BigDecimal("0.50")), at);
    }
}
