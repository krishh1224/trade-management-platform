package com.team5.portfolio;

import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.PositionResponse;
import com.team5.portfolio.dto.PortfolioSummaryResponse;
import com.team5.portfolio.dto.PricedPositionResponse;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.portfolio.fx.FxConverter;
import com.team5.portfolio.pnl.RealisedPnlProvider;
import com.team5.portfolio.pricing.PricedQuote;
import com.team5.portfolio.pricing.PricingService;
import com.team5.portfolio.read.InstrumentCurrency;
import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Prices an account's holdings. Quantity, average cost and cash come from {@link AccountService},
 * which owns them; this class only reads and calculates, and persists nothing.
 *
 * <p>Summary totals cover the holdings that could be priced and converted to the base currency, so
 * that {@code unrealisedPnl = marketValue - costBasis} always describes the same set of holdings.
 * {@code partial} is set whenever a holding is left out.
 */
@Service
public class PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String UNKNOWN_CURRENCY = "XXX";

    private final AccountService accountService;
    private final PortfolioAccessGuard accessGuard;
    private final PricingService pricingService;
    private final PortfolioReadMapper readMapper;
    private final RealisedPnlProvider realisedPnlProvider;
    private final Clock clock;

    public PortfolioService(
        AccountService accountService,
        PortfolioAccessGuard accessGuard,
        PricingService pricingService,
        PortfolioReadMapper readMapper,
        RealisedPnlProvider realisedPnlProvider,
        Clock clock
    ) {
        this.accountService = accountService;
        this.accessGuard = accessGuard;
        this.pricingService = pricingService;
        this.readMapper = readMapper;
        this.realisedPnlProvider = realisedPnlProvider;
        this.clock = clock;
    }

    public PortfolioSummaryResponse getSummary(long authenticatedAccountId, long accountId) {
        Valuation valuation = valuate(authenticatedAccountId, accountId);
        String base = valuation.baseCurrency();
        BigDecimal marketValue = valuation.marketValue();
        BigDecimal costBasis = valuation.costBasis();
        BigDecimal unrealised = marketValue.subtract(costBasis);
        return new PortfolioSummaryResponse(
            accountId,
            base,
            valuation.cash(),
            marketValue,
            costBasis,
            unrealised,
            percent(unrealised, costBasis),
            realisedPnlProvider.lifetimeRealised(accountId, base),
            valuation.cash().add(marketValue),
            valuation.positionCount(),
            valuation.partial(),
            clock.instant().truncatedTo(ChronoUnit.SECONDS)
        );
    }

    /**
     * Prices every open holding and converts it into the account's base currency. Holdings that cannot be
     * priced or converted are kept, uncounted, so callers can say so. Answers MKT-503 when there are
     * holdings and none could be priced.
     */
    public Valuation valuate(long authenticatedAccountId, long accountId) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        BalanceResponse balance = accountService.getBalance(authenticatedAccountId, accountId);
        String base = balance.currency();
        List<PositionResponse> held = openPositions(authenticatedAccountId, accountId, null);

        Map<String, String> currencies = currenciesOf(held);
        Set<String> symbols = new LinkedHashSet<>();
        held.forEach(position -> {
            symbols.add(position.symbol());
            String currency = currencies.getOrDefault(position.symbol(), UNKNOWN_CURRENCY);
            if (!currency.equals(base)) {
                symbols.add(FxConverter.pair(currency, base));
            }
        });
        Map<String, PricedQuote> quotes = priceOrFail(held, symbols);

        List<Valuation.ValuedHolding> holdings = toPricedPositions(accountId, held, currencies, quotes).stream()
            .map(position -> {
                Optional<BigDecimal> rate = position.lastPrice() == null
                    ? Optional.empty()
                    : FxConverter.rate(position.currency(), base, quotes);
                return rate.isEmpty()
                    ? new Valuation.ValuedHolding(position, null, null)
                    : new Valuation.ValuedHolding(position,
                        money(position.marketValue().multiply(rate.get())),
                        money(position.costBasis().multiply(rate.get())));
            })
            .toList();
        return new Valuation(base, balance.cashBalance(), held.size(), holdings);
    }

    public List<PricedPositionResponse> getPositions(long authenticatedAccountId, long accountId, String symbol) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        List<PositionResponse> held = openPositions(authenticatedAccountId, accountId, symbol);
        if (held.isEmpty()) {
            return List.of();
        }
        Map<String, String> currencies = currenciesOf(held);
        Set<String> symbols = held.stream().map(PositionResponse::symbol)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return toPricedPositions(accountId, held, currencies, priceOrFail(held, symbols));
    }

    private List<PositionResponse> openPositions(long authenticatedAccountId, long accountId, String symbol) {
        return accountService.getPositions(authenticatedAccountId, accountId).stream()
            .filter(position -> position.quantity() != null && position.quantity() > 0)
            .filter(position -> symbol == null || position.symbol().equals(symbol))
            .toList();
    }

    private Map<String, String> currenciesOf(List<PositionResponse> held) {
        if (held.isEmpty()) {
            return Map.of();
        }
        return readMapper.findCurrencies(held.stream().map(PositionResponse::symbol).toList()).stream()
            .collect(Collectors.toMap(InstrumentCurrency::symbol, InstrumentCurrency::currency, (a, b) -> a));
    }

    // Holdings exist but none could be priced: the one case that answers 503.
    private Map<String, PricedQuote> priceOrFail(List<PositionResponse> held, Set<String> symbols) {
        if (held.isEmpty()) {
            return Map.of();
        }
        Map<String, PricedQuote> quotes = pricingService.price(symbols);
        if (held.stream().noneMatch(position -> quotes.containsKey(position.symbol()))) {
            throw new PricingUnavailableException();
        }
        return quotes;
    }

    private List<PricedPositionResponse> toPricedPositions(
        long accountId,
        List<PositionResponse> held,
        Map<String, String> currencies,
        Map<String, PricedQuote> quotes
    ) {
        return held.stream()
            .map(position -> toPricedPosition(accountId, position,
                currencies.getOrDefault(position.symbol(), UNKNOWN_CURRENCY), quotes.get(position.symbol())))
            .toList();
    }

    private PricedPositionResponse toPricedPosition(
        long accountId, PositionResponse position, String currency, PricedQuote quote
    ) {
        BigDecimal quantity = BigDecimal.valueOf(position.quantity());
        BigDecimal costBasis = money(position.averageCost().multiply(quantity));
        if (quote == null) {
            return new PricedPositionResponse(accountId, position.symbol(), position.quantity(),
                position.averageCost(), costBasis, null, null, null, null, currency, null, true);
        }
        BigDecimal marketValue = money(quote.price().multiply(quantity));
        BigDecimal unrealised = marketValue.subtract(costBasis);
        return new PricedPositionResponse(accountId, position.symbol(), position.quantity(),
            position.averageCost(), costBasis, quote.price(), marketValue, unrealised,
            percent(unrealised, costBasis), currency, quote.asOf(), quote.stale());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal pnl, BigDecimal costBasis) {
        if (costBasis.signum() == 0) {
            return null;
        }
        return pnl.multiply(HUNDRED).divide(costBasis, 2, RoundingMode.HALF_UP);
    }
}
