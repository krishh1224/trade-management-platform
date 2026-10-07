package com.team5.advice;

import com.team5.dto.response.PositionResponse;
import com.team5.dto.response.WatchlistItemResponse;
import com.team5.portfolio.PortfolioService;
import com.team5.portfolio.dto.PricedPositionResponse;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.service.AccountService;
import com.team5.service.WatchlistService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Produces deterministic BUY/SELL/HOLD signals from the platform's own holdings, watchlist and
 * stored pricing data. Missing market data never fails the endpoint; the symbol is reported as
 * skipped instead.
 */
@Service
public class TradeAdviceService {

    private static final BigDecimal HOLDING_BUY_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal HOLDING_SELL_THRESHOLD = new BigDecimal("-10.00");
    private static final BigDecimal WATCHLIST_BUY_THRESHOLD = new BigDecimal("2.00");
    private static final BigDecimal WATCHLIST_SELL_THRESHOLD = new BigDecimal("-2.00");

    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final WatchlistService watchlistService;
    private final Clock clock;

    public TradeAdviceService(
        AccountService accountService,
        PortfolioService portfolioService,
        WatchlistService watchlistService,
        Clock clock
    ) {
        this.accountService = accountService;
        this.portfolioService = portfolioService;
        this.watchlistService = watchlistService;
        this.clock = clock;
    }

    public TradeAdviceResponse getSignals(long authenticatedAccountId, long accountId) {
        // Reuse the existing order-service account checks: same-account, account exists and ACTIVE.
        accountService.getAccount(authenticatedAccountId, accountId);

        List<AdviceSignalResponse> signals = new ArrayList<>();
        List<SkippedSignalResponse> skipped = new ArrayList<>();

        List<PositionResponse> rawHoldings = accountService.getPositions(authenticatedAccountId, accountId).stream()
            .filter(position -> position.quantity() != null && position.quantity() > 0)
            .toList();
        Map<String, PricedPositionResponse> pricedBySymbol = pricedHoldings(authenticatedAccountId, accountId);
        Set<String> holdingSymbols = new LinkedHashSet<>();

        for (PositionResponse holding : rawHoldings) {
            holdingSymbols.add(holding.symbol());
            PricedPositionResponse priced = pricedBySymbol.get(holding.symbol());
            if (priced == null || priced.lastPrice() == null || priced.unrealisedPnlPercent() == null) {
                skipped.add(new SkippedSignalResponse(
                    holding.symbol(),
                    null,
                    "HOLDING",
                    "No priced holding snapshot is available yet."
                ));
                continue;
            }

            String signal = priced.stale()
                ? "HOLD"
                : classifyHolding(priced.unrealisedPnlPercent());
            List<String> reasoning = new ArrayList<>();
            reasoning.add("Unrealised P&L is " + signedPercent(priced.unrealisedPnlPercent())
                + " versus average cost.");
            reasoning.add(priced.stale()
                ? "The stored quote is stale, so the signal stays HOLD until fresher market data arrives."
                : "The signal uses the latest stored holding price from the platform.");

            signals.add(new AdviceSignalResponse(
                priced.symbol(),
                null,
                "HOLDING",
                signal,
                List.copyOf(reasoning),
                priced.lastPrice(),
                null,
                priced.unrealisedPnlPercent(),
                priced.currency(),
                priced.priceAsOf(),
                priced.stale()
            ));
        }

        for (WatchlistItemResponse item : watchlistService.listWatchlist(authenticatedAccountId, null)) {
            if (holdingSymbols.contains(item.symbol())) {
                continue;
            }
            if (item.lastPrice() == null || item.changePct() == null) {
                skipped.add(new SkippedSignalResponse(
                    item.symbol(),
                    item.instrumentName(),
                    "WATCHLIST",
                    "The watchlist entry has no stored market move yet."
                ));
                continue;
            }

            signals.add(new AdviceSignalResponse(
                item.symbol(),
                item.instrumentName(),
                "WATCHLIST",
                classifyWatchlist(item.changePct()),
                List.of(
                    "The latest stored watchlist move is " + signedPercent(item.changePct()) + ".",
                    "Watchlist signals use current market movement because there is no holding cost basis yet."
                ),
                item.lastPrice(),
                item.changePct(),
                null,
                null,
                null,
                false
            ));
        }

        return new TradeAdviceResponse(
            accountId,
            Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
            List.copyOf(signals),
            List.copyOf(skipped)
        );
    }

    private Map<String, PricedPositionResponse> pricedHoldings(long authenticatedAccountId, long accountId) {
        try {
            List<PricedPositionResponse> positions = portfolioService.getPositions(authenticatedAccountId, accountId, null);
            Map<String, PricedPositionResponse> priced = new LinkedHashMap<>();
            for (PricedPositionResponse position : positions) {
                priced.put(position.symbol(), position);
            }
            return priced;
        } catch (PricingUnavailableException exception) {
            return Map.of();
        }
    }

    private static String classifyHolding(BigDecimal unrealisedPnlPercent) {
        if (unrealisedPnlPercent.compareTo(HOLDING_SELL_THRESHOLD) <= 0) {
            return "SELL";
        }
        if (unrealisedPnlPercent.compareTo(HOLDING_BUY_THRESHOLD) >= 0) {
            return "BUY";
        }
        return "HOLD";
    }

    private static String classifyWatchlist(BigDecimal changePct) {
        if (changePct.compareTo(WATCHLIST_BUY_THRESHOLD) >= 0) {
            return "BUY";
        }
        if (changePct.compareTo(WATCHLIST_SELL_THRESHOLD) <= 0) {
            return "SELL";
        }
        return "HOLD";
    }

    private static String signedPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        return (rounded.signum() > 0 ? "+" : "") + rounded.toPlainString() + "%";
    }
}