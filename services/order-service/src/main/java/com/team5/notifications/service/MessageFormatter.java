package com.team5.notifications.service;

import com.team5.notifications.api.AlertDirection;
import com.team5.notifications.api.PriceAlert;
import com.team5.notifications.domain.NotificationType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// Builds customer-facing text from structured fields only: symbol, side, quantity, price and a
// reason code. Anything that does not look like one of those is dropped, never echoed, so a
// credential, a name or an address cannot reach a customer through this module.
@Component
public class MessageFormatter {

    private static final Pattern SYMBOL = Pattern.compile("^[A-Za-z0-9.\\-^=]{1,20}$");
    private static final Pattern REASON_CODE = Pattern.compile("^[A-Z][A-Z_]{0,39}$");

    public String forTradeEvent(NotificationType type, OrderFacts facts) {
        String order = describeOrder(facts);

        return switch (type) {
            case ORDER_FILLED ->
                order + " was filled" + priceSuffix(facts.executedPrice() != null ? facts.executedPrice() : facts.price()) + ".";
            case ORDER_REJECTED -> order + " was rejected" + reasonSuffix(facts.reasonCode()) + ".";
            case ORDER_CANCELLED -> order + " was cancelled.";
            case PRICE_ALERT -> throw new IllegalArgumentException("PRICE_ALERT is not a trade event");
        };
    }

    public String forPriceAlert(PriceAlert alert) {
        String symbol = safeSymbol(alert.symbol());
        String instrument = symbol != null ? symbol : "An instrument on your watchlist";
        String movement = alert.direction() == AlertDirection.BELOW ? "fell below" : "rose above";
        return instrument + " " + movement + " your alert price of " + money(alert.threshold())
            + ". Last price " + money(alert.triggerPrice()) + ".";
    }

    // "Your BUY order for 10 AAPL" when the details are known, otherwise "Your order"
    private String describeOrder(OrderFacts facts) {
        String side = safeSide(facts.side());
        String symbol = safeSymbol(facts.symbol());
        if (side == null || symbol == null || facts.quantity() == null) {
            return "Your order" + (symbol != null ? " for " + symbol : "");
        }
        return "Your " + side + " order for " + facts.quantity() + " " + symbol;
    }

    private String safeSide(String side) {
        if ("BUY".equalsIgnoreCase(side) || "SELL".equalsIgnoreCase(side)) {
            return side.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String safeSymbol(String symbol) {
        return symbol != null && SYMBOL.matcher(symbol).matches() ? symbol : null;
    }

    // INSUFFICIENT_FUNDS -> " (insufficient funds)"; anything that is not a code is dropped
    private String reasonSuffix(String reason) {
        if (reason == null || !REASON_CODE.matcher(reason).matches()) {
            return "";
        }
        return " (" + reason.toLowerCase(Locale.ROOT).replace('_', ' ') + ")";
    }

    private String priceSuffix(BigDecimal price) {
        return price == null ? "" : " at " + money(price);
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
