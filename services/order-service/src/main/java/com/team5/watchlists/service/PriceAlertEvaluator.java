package com.team5.watchlists.service;

import com.team5.notifications.api.AlertDelivery;
import com.team5.notifications.api.PriceAlert;
import com.team5.watchlists.domain.CrossingRule;
import com.team5.watchlists.domain.LastQuote;
import com.team5.watchlists.domain.MarketQuote;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.QuoteEvaluation;
import com.team5.watchlists.mapper.PriceAlertMapper;
import com.team5.watchlists.mapper.WatchlistQuoteMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one market-data quote: moves the symbol's stored price on, and triggers every active alert
 * the move crossed (see {@link CrossingRule}). A triggered alert is handed to the notifications module
 * through {@link AlertDelivery}, which records it for the dispatcher and returns: no provider is
 * called here, and the customer's channel is resolved there, never in this module.
 *
 * Everything for one quote is one transaction. If anything fails, the alert stays ACTIVE and the
 * stored price is not moved, so the same crossing is found again when the quote is retried.
 */
@Service
public class PriceAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertEvaluator.class);

    private final WatchlistQuoteMapper quoteMapper;
    private final PriceAlertMapper alertMapper;
    private final AlertDelivery alertDelivery;
    private final Clock clock;

    @Autowired
    public PriceAlertEvaluator(WatchlistQuoteMapper quoteMapper, PriceAlertMapper alertMapper, AlertDelivery alertDelivery) {
        this(quoteMapper, alertMapper, alertDelivery, Clock.systemUTC());
    }

    PriceAlertEvaluator(WatchlistQuoteMapper quoteMapper, PriceAlertMapper alertMapper, AlertDelivery alertDelivery, Clock clock) {
        this.quoteMapper = quoteMapper;
        this.alertMapper = alertMapper;
        this.alertDelivery = alertDelivery;
        this.clock = clock;
    }

    @Transactional
    public QuoteEvaluation evaluate(MarketQuote quote) {
        String symbol = quote.symbol();
        BigDecimal current = quote.price();

        if (quoteMapper.insertIfAbsent(symbol, current, quote.quotedAt()) == 1) {
            // first price seen for the symbol: nothing to have crossed from
            return QuoteEvaluation.seeded();
        }

        // row-locked, so two consumers briefly sharing a partition during a rebalance take turns
        LastQuote previous = quoteMapper.lockLastQuote(symbol);
        if (!quote.quotedAt().isAfter(previous.quotedAt())) {
            // a redelivery, or a replay of older quotes: measuring from the newer stored price would
            // invent crossings, so it changes nothing
            return QuoteEvaluation.stale();
        }
        quoteMapper.update(symbol, current, quote.quotedAt());

        BigDecimal before = previous.lastPrice();
        if (before.compareTo(current) == 0) {
            return QuoteEvaluation.evaluated(0);
        }

        // only the symbol's ACTIVE alerts with a threshold between the two prices, from the index
        List<PriceAlertRow> candidates =
            alertMapper.findActiveBySymbolBetween(symbol, before.min(current), before.max(current));

        int triggered = 0;
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (PriceAlertRow alert : candidates) {
            if (!CrossingRule.crossed(alert.getDirection(), alert.getThreshold(), before, current)) {
                continue;
            }
            if (alertMapper.markTriggered(alert.getAlertId(), current, now) != 1) {
                // deleted or already triggered by the time this quote got to it
                continue;
            }
            boolean recorded = alertDelivery.deliver(new PriceAlert(
                alert.getDeliveryId(),
                alert.getAccountId(),
                alert.getSymbol(),
                alert.getThreshold(),
                current,
                alert.getDirection(),
                now
            ));
            triggered++;
            log.info("Price alert {} triggered: {} {} {} (previous {}, now {}), {}",
                alert.getAlertId(), symbol, alert.getDirection(), alert.getThreshold(), before, current,
                recorded ? "handed to notifications" : "already recorded by notifications");
        }
        return QuoteEvaluation.evaluated(triggered);
    }
}
