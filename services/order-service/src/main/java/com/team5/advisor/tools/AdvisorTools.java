package com.team5.advisor.tools;

import com.team5.advisor.data.AdvisorRepository;
import com.team5.advisor.data.AdvisorRepository.AccountRow;
import com.team5.advisor.data.AdvisorRepository.FxRow;
import com.team5.advisor.data.AdvisorRepository.InstrumentRow;
import com.team5.advisor.data.AdvisorRepository.WatchlistRow;
import com.team5.advisor.tools.ToolViews.AdviceSignalView;
import com.team5.advisor.tools.ToolViews.AdviceSignalsView;
import com.team5.advisor.tools.ToolViews.FxRateView;
import com.team5.advisor.tools.ToolViews.InstrumentView;
import com.team5.advisor.tools.ToolViews.OrderView;
import com.team5.advisor.tools.ToolViews.PortfolioView;
import com.team5.advisor.tools.ToolViews.QuoteView;
import com.team5.advisor.tools.ToolViews.SkippedAdviceView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The read-only tools the model may call. The account is always taken from the server-side tool
 * context (set from the caller's JWT), never from a model-supplied argument, so the model cannot
 * read another account even if a prompt asks it to.
 */
@Component
public class AdvisorTools {

    public static final String CTX_ACCOUNT_ID = "accountId";
    public static final String CTX_BASE_CURRENCY = "baseCurrency";
    public static final String CTX_TRACKER = "toolCallTracker";

    static final int MAX_QUOTE_SYMBOLS = 10;
    static final int MAX_SEARCH_RESULTS = 15;
    static final int DEFAULT_ORDERS = 10;
    static final int MAX_ORDERS = 50;
    private static final BigDecimal HOLDING_BUY_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal HOLDING_SELL_THRESHOLD = new BigDecimal("-10.00");
    private static final BigDecimal WATCHLIST_BUY_THRESHOLD = new BigDecimal("2.00");
    private static final BigDecimal WATCHLIST_SELL_THRESHOLD = new BigDecimal("-2.00");

    private final AdvisorRepository repository;

    public AdvisorTools(AdvisorRepository repository) {
        this.repository = repository;
    }

    @Tool(name = "get_portfolio", description = """
        Returns the signed-in user's cash balance and every holding: quantity, average cost, last price \
        and its age, today's change, market value, unrealised gain percent, the value in the account's \
        base currency and each holding's share of the total. Call this for any question about the user's \
        holdings, positions, allocation, exposure, cash or net worth.""")
    public PortfolioView getPortfolio(ToolContext toolContext) {
        track(toolContext, "get_portfolio");
        long accountId = accountId(toolContext);
        AccountRow account = repository.findAccount(accountId)
            .orElseThrow(() -> new IllegalStateException("Account not found"));
        return PortfolioValuer.value(
            account.baseCurrency(),
            account.balance(),
            repository.findHoldings(accountId),
            repository::latestSettledFxRate
        );
    }

    @Tool(name = "get_quotes", description = """
        Returns the latest stored price for up to 10 instrument symbols (for example AAPL, INFY.NS, \
        FX:USDINR, X:BTC-USD), with the currency, today's change and how many minutes old the price is. \
        Prices are refreshed periodically by the platform, not live. priceAvailable is false when the \
        platform has no stored price for that symbol yet.""")
    public List<QuoteView> getQuotes(
        @ToolParam(description = "Instrument symbols exactly as listed on the platform, at most 10") List<String> symbols,
        ToolContext toolContext
    ) {
        track(toolContext, "get_quotes");
        List<String> requested = normaliseSymbols(symbols);
        Map<String, InstrumentRow> found = repository.findInstruments(requested).stream()
            .collect(Collectors.toMap(row -> row.symbol().toUpperCase(Locale.ROOT), Function.identity()));

        List<QuoteView> quotes = new ArrayList<>();
        for (String symbol : requested) {
            InstrumentRow row = found.get(symbol);
            if (row == null) {
                quotes.add(new QuoteView(symbol, "Not an instrument on this platform", null, null,
                    false, null, null, null, null));
            } else {
                quotes.add(new QuoteView(row.symbol(), row.name(), row.exchange(), row.currency(),
                    row.lastPrice() != null, row.lastPrice(), row.changePct(), row.priceUpdatedAt(),
                    row.priceAgeMinutes()));
            }
        }
        return quotes;
    }

    @Tool(name = "get_fx_rate", description = """
        Returns the most recent exchange rate the platform used to convert the given currency into the \
        account's base currency (INR for all accounts), taken from the last settled order in that \
        currency. Use it to explain or estimate conversions. available is false when no order in that \
        currency has settled yet.""")
    public FxRateView getFxRate(
        @ToolParam(description = "ISO 4217 currency code to convert from, for example USD") String currency,
        ToolContext toolContext
    ) {
        track(toolContext, "get_fx_rate");
        String from = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        String to = String.valueOf(toolContext.getContext().get(CTX_BASE_CURRENCY));
        if (from.equals(to)) {
            return new FxRateView(from, to, true, BigDecimal.ONE, null, "Same currency, no conversion");
        }
        Optional<FxRow> fx = repository.latestSettledFxRate(from, to);
        return fx
            .map(row -> new FxRateView(from, to, true, row.rate(), row.asOf(), "Most recent settled order"))
            .orElseGet(() -> new FxRateView(from, to, false, null, null, "No settled order in this currency yet"));
    }

    @Tool(name = "search_instruments", description = """
        Searches the instruments available on the platform by symbol, company name, exchange (NSE, BSE, \
        NASDAQ, NYSE, FOREX, CRYPTO) or asset class (EQUITY, FX, CRYPTO). Returns up to 15 matches with \
        currency, whether they are tradable and the latest stored price.""")
    public List<InstrumentView> searchInstruments(
        @ToolParam(description = "Part of a symbol or company name, or an exchange or asset class") String query,
        ToolContext toolContext
    ) {
        track(toolContext, "search_instruments");
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return repository.searchInstruments(query, MAX_SEARCH_RESULTS).stream()
            .map(row -> new InstrumentView(row.symbol(), row.name(), row.exchange(), row.currency(),
                row.assetClass(), row.tradable(), row.lastPrice(), row.priceUpdatedAt(), row.priceAgeMinutes()))
            .toList();
    }

    @Tool(name = "get_order_history", description = """
        Returns the signed-in user's most recent orders, newest first: symbol, side, quantity, status, \
        limit and executed price in the instrument's currency, and for filled orders the amount actually \
        debited or credited in the account's base currency. Includes rejected and cancelled orders.""")
    public List<OrderView> getOrderHistory(
        @ToolParam(description = "How many orders to return, 1 to 50 (default 10)", required = false) Integer limit,
        ToolContext toolContext
    ) {
        track(toolContext, "get_order_history");
        int size = limit == null ? DEFAULT_ORDERS : Math.max(1, Math.min(MAX_ORDERS, limit));
        return repository.findRecentOrders(accountId(toolContext), size).stream()
            .map(row -> new OrderView(row.symbol(), row.side(), row.quantity(), row.status(),
                row.instrumentCurrency(), row.limitPrice(), row.executedPrice(), row.settlementAmount(),
                row.settlementCurrency(), row.rejectionReason(), row.createdOn()))
            .toList();
    }

    @Tool(name = "get_advice_signals", description = """
        Returns the platform's current BUY, SELL and HOLD signals for the signed-in user's holdings and watchlist,
        including the rule-based reasoning behind each signal and any skipped symbols where data was insufficient.
        Call this when the user asks why a symbol is BUY/SELL/HOLD, what the current signals are, or to explain
        the platform's advice output.""")
    public AdviceSignalsView getAdviceSignals(ToolContext toolContext) {
        track(toolContext, "get_advice_signals");
        long accountId = accountId(toolContext);

        List<AdviceSignalView> signals = new ArrayList<>();
        List<SkippedAdviceView> skipped = new ArrayList<>();
        List<AdvisorRepository.HoldingRow> holdings = repository.findHoldings(accountId);
        Set<String> holdingSymbols = new LinkedHashSet<>();

        for (AdvisorRepository.HoldingRow holding : holdings) {
            holdingSymbols.add(holding.symbol());
            if (holding.lastPrice() == null) {
                skipped.add(new SkippedAdviceView(
                    holding.symbol(), holding.name(), "HOLDING", "No priced holding snapshot is available yet."));
                continue;
            }

            BigDecimal unrealisedPercent = unrealisedGainPercent(holding.avgCost(), holding.lastPrice());
            String signal = classifyHolding(unrealisedPercent);
            signals.add(new AdviceSignalView(
                holding.symbol(),
                holding.name(),
                "HOLDING",
                signal,
                List.of(
                    "Unrealised P&L is " + signedPercent(unrealisedPercent) + " versus average cost.",
                    ageReason(holding.priceAgeMinutes())
                ),
                holding.lastPrice(),
                holding.changePct(),
                unrealisedPercent,
                holding.currency(),
                holding.priceUpdatedAt(),
                holding.priceAgeMinutes()
            ));
        }

        for (WatchlistRow row : repository.findWatchlist(accountId)) {
            if (holdingSymbols.contains(row.symbol())) {
                continue;
            }
            if (row.lastPrice() == null || row.changePct() == null) {
                skipped.add(new SkippedAdviceView(
                    row.symbol(), row.name(), "WATCHLIST", "The watchlist entry has no stored market move yet."));
                continue;
            }

            signals.add(new AdviceSignalView(
                row.symbol(),
                row.name(),
                "WATCHLIST",
                classifyWatchlist(row.changePct()),
                List.of(
                    "The latest stored watchlist move is " + signedPercent(row.changePct()) + ".",
                    ageReason(row.priceAgeMinutes())
                ),
                row.lastPrice(),
                row.changePct(),
                null,
                row.currency(),
                row.priceUpdatedAt(),
                row.priceAgeMinutes()
            ));
        }

        return new AdviceSignalsView(List.copyOf(signals), List.copyOf(skipped));
    }

    static List<String> normaliseSymbols(List<String> symbols) {
        if (symbols == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                unique.add(symbol.trim().toUpperCase(Locale.ROOT));
            }
            if (unique.size() == MAX_QUOTE_SYMBOLS) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private static long accountId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(CTX_ACCOUNT_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("No account in tool context");
    }

    private static void track(ToolContext toolContext, String toolName) {
        if (toolContext.getContext().get(CTX_TRACKER) instanceof ToolCallTracker tracker) {
            tracker.record(toolName);
        }
    }

    private static BigDecimal unrealisedGainPercent(BigDecimal averageCost, BigDecimal lastPrice) {
        if (averageCost == null || averageCost.signum() == 0 || lastPrice == null) {
            return BigDecimal.ZERO;
        }
        return lastPrice.subtract(averageCost)
            .multiply(BigDecimal.valueOf(100))
            .divide(averageCost, 2, java.math.RoundingMode.HALF_UP);
    }

    private static String classifyHolding(BigDecimal unrealisedGainPercent) {
        if (unrealisedGainPercent.compareTo(HOLDING_SELL_THRESHOLD) <= 0) {
            return "SELL";
        }
        if (unrealisedGainPercent.compareTo(HOLDING_BUY_THRESHOLD) >= 0) {
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
        return (value.signum() > 0 ? "+" : "") + value.toPlainString() + "%";
    }

    private static String ageReason(Long priceAgeMinutes) {
        if (priceAgeMinutes == null) {
            return "No quote age is available for this stored price.";
        }
        if (priceAgeMinutes < 60) {
            return "The signal uses a stored price refreshed about " + priceAgeMinutes + " minutes ago.";
        }
        if (priceAgeMinutes < 1440) {
            return "The signal uses a stored price refreshed about " + (priceAgeMinutes / 60) + " hours ago.";
        }
        return "The signal uses a stored price that is more than a day old and may be out of date.";
    }
}
