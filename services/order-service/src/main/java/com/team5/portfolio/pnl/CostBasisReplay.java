package com.team5.portfolio.pnl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out the average cost of a holding at the moment of each sale.
 *
 * <p>The executor keeps a weighted average cost per holding: a buy re-weights it as
 * {@code (oldQty * oldAvg + fillQty * fillPrice) / (oldQty + fillQty)} rounded half-up to 4 places, a
 * sell leaves it unchanged, and a fully sold holding is deleted. Nothing records the average at the
 * time of a sale, so it is rebuilt by replaying the fills in order.
 *
 * <p>The replay is anchored on the holding as it is now when there is one. Reversing the fills from the
 * current quantity and cost gives the position the account started from, which also covers an opening
 * position that was loaded straight into the holdings without an order. Without a current holding
 * (fully sold) the replay starts from nothing. A sale the history cannot explain, because it sells more
 * than the replay holds, has no average cost and is left out.
 */
public final class CostBasisReplay {

    private static final int AVERAGE_COST_SCALE = 4;

    /** One filled order for a single instrument, in the instrument's currency. */
    public record Fill(long orderId, boolean sell, long quantity, BigDecimal price) {
    }

    /** The holding as it stands now. */
    public record Holding(long quantity, BigDecimal averageCost) {
    }

    private record Position(long quantity, BigDecimal averageCost) {
    }

    private CostBasisReplay() {
    }

    /**
     * @param fills   fills of one instrument, oldest first
     * @param current the holding now, or null when the instrument is no longer held
     * @return average cost at the time of each sale that can be explained, keyed by order id
     */
    public static Map<Long, BigDecimal> averageCostAtSale(List<Fill> fills, Holding current) {
        Position position = opening(fills, current);
        Map<Long, BigDecimal> result = new HashMap<>();

        for (Fill fill : fills) {
            if (!fill.sell()) {
                position = afterBuy(position, fill);
                continue;
            }
            if (position.quantity() < fill.quantity() || position.averageCost().signum() <= 0) {
                // Sells more than the history holds, or holds it at an unknown cost: nothing to book.
                position = new Position(Math.max(position.quantity() - fill.quantity(), 0), position.averageCost());
                continue;
            }
            result.put(fill.orderId(), position.averageCost());
            long remaining = position.quantity() - fill.quantity();
            position = new Position(remaining, remaining == 0 ? BigDecimal.ZERO : position.averageCost());
        }
        return result;
    }

    // The position before the first fill, found by undoing every fill from the position held now.
    private static Position opening(List<Fill> fills, Holding current) {
        if (current == null) {
            return new Position(0, BigDecimal.ZERO);
        }
        Position position = new Position(current.quantity(),
            current.averageCost().setScale(AVERAGE_COST_SCALE, RoundingMode.HALF_UP));
        for (int i = fills.size() - 1; i >= 0; i--) {
            Fill fill = fills.get(i);
            if (fill.sell()) {
                // A sell leaves the average unchanged, so undoing it only gives the units back.
                position = new Position(position.quantity() + fill.quantity(), position.averageCost());
            } else {
                position = beforeBuy(position, fill);
            }
        }
        return position;
    }

    private static Position afterBuy(Position position, Fill fill) {
        long quantity = position.quantity() + fill.quantity();
        BigDecimal cost = position.averageCost().multiply(BigDecimal.valueOf(position.quantity()))
            .add(fill.price().multiply(BigDecimal.valueOf(fill.quantity())));
        return new Position(quantity, cost.divide(BigDecimal.valueOf(quantity), AVERAGE_COST_SCALE, RoundingMode.HALF_UP));
    }

    private static Position beforeBuy(Position after, Fill fill) {
        long before = after.quantity() - fill.quantity();
        if (before <= 0) {
            // The buy opened the position (or the history does not add up): nothing was held before it.
            return new Position(0, BigDecimal.ZERO);
        }
        BigDecimal cost = after.averageCost().multiply(BigDecimal.valueOf(after.quantity()))
            .subtract(fill.price().multiply(BigDecimal.valueOf(fill.quantity())));
        BigDecimal average = cost.divide(BigDecimal.valueOf(before), AVERAGE_COST_SCALE, RoundingMode.HALF_UP);
        return new Position(before, average.max(BigDecimal.ZERO));
    }
}
