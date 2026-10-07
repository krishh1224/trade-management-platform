package com.team5.watchlists.domain;

import com.team5.notifications.api.AlertDirection;
import java.math.BigDecimal;

/**
 * When a move from {@code previous} to {@code current} crosses an alert's threshold.
 *
 * <pre>
 *   ABOVE: previous &lt; threshold AND current &gt;= threshold
 *   BELOW: previous &gt; threshold AND current &lt;= threshold
 * </pre>
 *
 * A price already beyond the threshold before the move is not a crossing, so an alert does not fire
 * on every quote while the price stays there. With no previous price nothing can be crossed.
 */
public final class CrossingRule {

    private CrossingRule() {
    }

    public static boolean crossed(AlertDirection direction, BigDecimal threshold, BigDecimal previous, BigDecimal current) {
        if (direction == null || threshold == null || previous == null || current == null) {
            return false;
        }
        return switch (direction) {
            case ABOVE -> previous.compareTo(threshold) < 0 && current.compareTo(threshold) >= 0;
            case BELOW -> previous.compareTo(threshold) > 0 && current.compareTo(threshold) <= 0;
        };
    }
}
