package com.team5.watchlists.service;

import static com.team5.notifications.api.AlertDirection.ABOVE;
import static com.team5.notifications.api.AlertDirection.BELOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.notifications.InMemoryNotificationMapper;
import com.team5.notifications.api.AlertDelivery;
import com.team5.notifications.delivery.ChannelSender;
import com.team5.notifications.delivery.ChannelSenderRegistry;
import com.team5.notifications.delivery.DeliveryLedger;
import com.team5.notifications.delivery.NotificationDispatcher;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.service.MessageFormatter;
import com.team5.notifications.service.NotificationServiceImpl;
import com.team5.preferences.AlertChannel;
import com.team5.watchlists.InMemoryAlertStore;
import com.team5.watchlists.domain.MarketQuote;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.PriceAlertStatus;
import com.team5.watchlists.domain.QuoteEvaluation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The real notifications service and dispatcher sit behind AlertDelivery here, so "delivered" means a
// PRICE_ALERT notification row was recorded and sent on the channel preferences hold, not a log line.
class PriceAlertEvaluatorTest {

    private static final long OWNER = 101L;
    private static final String INFY = "INFY.NS";
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-07T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T09:30:00Z"), ZoneOffset.UTC);

    private InMemoryAlertStore store;
    private InMemoryNotificationMapper notifications;
    private List<Notification> sent;
    private NotificationDispatcher dispatcher;
    private PriceAlertEvaluator evaluator;
    private int quoteNumber;

    @BeforeEach
    void setUp() {
        store = new InMemoryAlertStore();
        notifications = new InMemoryNotificationMapper();
        sent = new ArrayList<>();
        AlertDelivery alertDelivery =
            new NotificationServiceImpl(notifications, new MessageFormatter(), new AccountAccessAuthorizer());
        evaluator = new PriceAlertEvaluator(store.quoteMapper, store.alertMapper, alertDelivery, CLOCK);

        ChannelSender email = new ChannelSender() {
            @Override
            public AlertChannel channel() {
                return AlertChannel.EMAIL;
            }

            @Override
            public void send(Notification notification) {
                sent.add(notification);
            }
        };
        dispatcher = new NotificationDispatcher(
            notifications,
            accountId -> Optional.of(AlertChannel.EMAIL),
            new ChannelSenderRegistry(List.of(email)),
            new DeliveryLedger(notifications),
            50
        );
        quoteNumber = 0;
    }

    @Test
    void quoteShortOfTheThreshold_doesNotTrigger() {
        PriceAlertRow alert = store.addActive(OWNER, INFY, ABOVE, "1500.00");

        quote(INFY, "1450.00");
        QuoteEvaluation result = quote(INFY, "1499.99");

        assertEquals(QuoteEvaluation.Outcome.EVALUATED, result.outcome());
        assertEquals(0, result.triggered());
        assertEquals(PriceAlertStatus.ACTIVE, store.alert(alert.getAlertId()).getStatus());
        assertEquals(0, notifications.rows().size());
    }

    @Test
    void quoteCrossingAbove_triggersAndIsDeliveredThroughNotifications() {
        PriceAlertRow alert = store.addActive(OWNER, INFY, ABOVE, "1500.00");

        quote(INFY, "1490.00");
        QuoteEvaluation result = quote(INFY, "1502.35");

        assertEquals(1, result.triggered());
        PriceAlertRow stored = store.alert(alert.getAlertId());
        assertEquals(PriceAlertStatus.TRIGGERED, stored.getStatus());
        assertEquals(new BigDecimal("1502.35"), stored.getTriggerPrice());
        assertEquals(OffsetDateTime.now(CLOCK), stored.getTriggeredAt());

        // recorded by the notifications module, keyed by the alert's delivery id ...
        Notification row = notifications.rows().get(0);
        assertEquals(NotificationType.PRICE_ALERT, row.getType());
        assertEquals(OWNER, row.getAccountId());
        assertEquals(alert.getDeliveryId(), row.getEventId());
        assertEquals("INFY.NS rose above your alert price of 1500.00. Last price 1502.35.", row.getMessage());
        assertEquals(NotificationStatus.QUEUED, row.getStatus());

        // ... and sent on the channel the customer's preferences hold, resolved there and not here
        dispatcher.dispatchPending();
        assertEquals(1, sent.size());
        assertEquals(NotificationStatus.SENT, notifications.rows().get(0).getStatus());
        assertEquals(AlertChannel.EMAIL, notifications.rows().get(0).getChannel());
    }

    @Test
    void quoteCrossingBelow_triggers() {
        PriceAlertRow alert = store.addActive(OWNER, INFY, BELOW, "1400.00");

        quote(INFY, "1410.00");
        QuoteEvaluation result = quote(INFY, "1400.00");

        assertEquals(1, result.triggered());
        assertEquals(PriceAlertStatus.TRIGGERED, store.alert(alert.getAlertId()).getStatus());
        assertEquals("INFY.NS fell below your alert price of 1400.00. Last price 1400.00.",
            notifications.rows().get(0).getMessage());
    }

    @Test
    void priceStayingBeyondTheThreshold_doesNotFireOnEveryQuote() {
        store.addActive(OWNER, INFY, ABOVE, "1500.00");

        quote(INFY, "1490.00");
        quote(INFY, "1510.00");   // crosses
        quote(INFY, "1520.00");   // stays above
        quote(INFY, "1495.00");   // dips back below
        quote(INFY, "1505.00");   // crosses again, but the alert is one-shot
        quote(INFY, "1530.00");

        assertEquals(1, notifications.rows().size(), "one notification for one alert");
        dispatcher.dispatchPending();
        assertEquals(1, sent.size());
    }

    @Test
    void alertCreatedWhileThePriceIsAlreadyBeyond_waitsForAFreshCrossing() {
        quote(INFY, "1600.00");
        PriceAlertRow alert = store.addActive(OWNER, INFY, ABOVE, "1500.00");

        quote(INFY, "1610.00");
        assertEquals(PriceAlertStatus.ACTIVE, store.alert(alert.getAlertId()).getStatus());

        quote(INFY, "1480.00");
        quote(INFY, "1500.00");
        assertEquals(PriceAlertStatus.TRIGGERED, store.alert(alert.getAlertId()).getStatus());
    }

    @Test
    void firstQuoteForASymbol_onlySeedsThePrice() {
        PriceAlertRow alert = store.addActive(OWNER, INFY, ABOVE, "1500.00");

        QuoteEvaluation result = quote(INFY, "1600.00");

        assertEquals(QuoteEvaluation.Outcome.SEEDED, result.outcome());
        assertEquals(PriceAlertStatus.ACTIVE, store.alert(alert.getAlertId()).getStatus());
        assertEquals(new BigDecimal("1600.00"), store.quote(INFY).lastPrice());
    }

    @Test
    void redeliveredOrReplayedQuote_changesNothingAndSendsNothingTwice() {
        store.addActive(OWNER, INFY, ABOVE, "1500.00");
        MarketQuote below = new MarketQuote(UUID.randomUUID(), INFY, new BigDecimal("1490.00"), T0);
        MarketQuote above = new MarketQuote(UUID.randomUUID(), INFY, new BigDecimal("1510.00"), T0.plusMinutes(1));

        evaluator.evaluate(below);
        evaluator.evaluate(above);
        // the same quote again, then a replay from the start of the topic
        assertEquals(QuoteEvaluation.Outcome.STALE, evaluator.evaluate(above).outcome());
        assertEquals(QuoteEvaluation.Outcome.STALE, evaluator.evaluate(below).outcome());
        assertEquals(QuoteEvaluation.Outcome.STALE, evaluator.evaluate(above).outcome());

        assertEquals(1, notifications.rows().size());
        assertEquals(new BigDecimal("1510.00"), store.quote(INFY).lastPrice(), "a replay does not move the price back");
    }

    @Test
    void onlyTheQuotedSymbolsAlertsAreEvaluated() {
        PriceAlertRow infy = store.addActive(OWNER, INFY, ABOVE, "1500.00");
        PriceAlertRow tcs = store.addActive(OWNER, "TCS.NS", ABOVE, "1500.00");

        quote(INFY, "1490.00");
        quote(INFY, "1510.00");

        assertEquals(PriceAlertStatus.TRIGGERED, store.alert(infy.getAlertId()).getStatus());
        assertEquals(PriceAlertStatus.ACTIVE, store.alert(tcs.getAlertId()).getStatus());
    }

    @Test
    void everyAccountsAlertOnTheSymbolIsEvaluated_eachDeliveredToItsOwnAccount() {
        store.addActive(OWNER, INFY, ABOVE, "1500.00");
        store.addActive(202L, INFY, ABOVE, "1505.00");
        store.addActive(303L, INFY, ABOVE, "1600.00");   // not reached

        quote(INFY, "1490.00");
        QuoteEvaluation result = quote(INFY, "1510.00");

        assertEquals(2, result.triggered());
        assertEquals(List.of(OWNER, 202L), notifications.rows().stream().map(Notification::getAccountId).toList());
    }

    @Test
    void unchangedPrice_skipsTheAlertLookup() {
        store.addActive(OWNER, INFY, ABOVE, "1500.00");

        quote(INFY, "1490.00");
        quote(INFY, "1490.00");

        assertEquals(0, store.symbolLookups());
    }

    @Test
    void notificationsRefusingTheHandOff_propagates_soTheQuoteIsRetried() {
        AlertDelivery refusing = alert -> {
            throw new IllegalStateException("notifications unavailable");
        };
        PriceAlertEvaluator failing = new PriceAlertEvaluator(store.quoteMapper, store.alertMapper, refusing, CLOCK);
        store.addActive(OWNER, INFY, ABOVE, "1500.00");
        failing.evaluate(new MarketQuote(UUID.randomUUID(), INFY, new BigDecimal("1490.00"), T0));

        // in production the surrounding transaction then rolls back (PriceAlertMapperIntegrationTest
        // shows it on Postgres) and the listener does not acknowledge, so Kafka redelivers
        assertThrows(IllegalStateException.class,
            () -> failing.evaluate(new MarketQuote(UUID.randomUUID(), INFY, new BigDecimal("1510.00"), T0.plusMinutes(1))));
        assertEquals(0, notifications.rows().size());
    }

    private QuoteEvaluation quote(String symbol, String price) {
        return evaluator.evaluate(new MarketQuote(UUID.randomUUID(), symbol, new BigDecimal(price), T0.plusSeconds(quoteNumber++)));
    }
}
