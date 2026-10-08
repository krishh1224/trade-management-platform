package com.team5.notifications.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.team5.notifications.api.AlertDirection;
import com.team5.notifications.api.PriceAlert;
import com.team5.notifications.domain.NotificationType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageFormatterTest {

    private final MessageFormatter formatter = new MessageFormatter();

    @Test
    void filled_usesTheExecutedPrice() {
        String message = formatter.forTradeEvent(NotificationType.ORDER_FILLED,
            new OrderFacts("AAPL", "buy", 10, new BigDecimal("190"), new BigDecimal("187.2"), null));

        assertEquals("Your BUY order for 10 AAPL was filled at 187.20.", message);
    }

    @Test
    void rejected_turnsTheReasonCodeIntoWords() {
        String message = formatter.forTradeEvent(NotificationType.ORDER_REJECTED,
            new OrderFacts("TSLA", "SELL", 5, null, null, "INSUFFICIENT_HOLDINGS"));

        assertEquals("Your SELL order for 5 TSLA was rejected (insufficient holdings).", message);
    }

    @Test
    void reasonThatIsNotACode_isNeverEchoedToTheCustomer() {
        String leaked = "token=eyJhbGciOi... card 4111111111111111 jane@example.com";
        String message = formatter.forTradeEvent(NotificationType.ORDER_REJECTED,
            new OrderFacts("TSLA", "SELL", 5, null, null, leaked));

        assertEquals("Your SELL order for 5 TSLA was rejected.", message);
    }

    @Test
    void symbolThatIsNotASymbol_isNeverEchoedToTheCustomer() {
        String message = formatter.forTradeEvent(NotificationType.ORDER_CANCELLED,
            new OrderFacts("<script>alert(1)</script>", "BUY", 1, BigDecimal.ONE, null, null));

        assertEquals("Your order was cancelled.", message);
        assertFalse(message.contains("script"));
    }

    @Test
    void titleIsDerivedFromTheTypeNotStored() {
        assertEquals("Order filled", NotificationType.ORDER_FILLED.title());
        assertEquals("Order rejected", NotificationType.ORDER_REJECTED.title());
        assertEquals("Order cancelled", NotificationType.ORDER_CANCELLED.title());
        assertEquals("Price alert", NotificationType.PRICE_ALERT.title());
    }

    @Test
    void priceAlert_isWrittenFromStructuredFieldsOnly() {
        PriceAlert alert = new PriceAlert(UUID.randomUUID(), 101L, "INFY.NS", new BigDecimal("1400"),
            new BigDecimal("1395.5"), AlertDirection.BELOW, OffsetDateTime.now());

        String message = formatter.forPriceAlert(alert);

        assertEquals("INFY.NS fell below your alert price of 1400.00. Last price 1395.50.", message);
    }
}
