package com.team5.portfolio;

import com.team5.portfolio.dto.PnlResponse;
import com.team5.portfolio.dto.SymbolPnlResponse;
import com.team5.portfolio.exception.InvalidInputException;
import com.team5.portfolio.pnl.RealisedPnlLedger;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Realised and unrealised profit and loss for an account.
 *
 * <p>Realised comes from the ledger and can be bounded by {@code from} and {@code to}; unrealised is
 * always as at now, from the same valuation the portfolio summary uses, so the two routes agree.
 * Unrealised covers the holdings that could be priced and converted, exactly as the summary's does.
 */
@Service
public class PnlService {

    private final PortfolioAccessGuard accessGuard;
    private final PortfolioService portfolioService;
    private final RealisedPnlLedger ledger;
    private final Clock clock;

    public PnlService(PortfolioAccessGuard accessGuard, PortfolioService portfolioService,
                      RealisedPnlLedger ledger, Clock clock) {
        this.accessGuard = accessGuard;
        this.portfolioService = portfolioService;
        this.ledger = ledger;
        this.clock = clock;
    }

    public PnlResponse getPnl(long authenticatedAccountId, long accountId, LocalDate from, LocalDate to,
                              boolean bySymbol) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidInputException();
        }

        Valuation valuation = portfolioService.valuate(authenticatedAccountId, accountId);
        String base = valuation.baseCurrency();
        BigDecimal realised = ledger.realised(accountId, base, from, to);
        BigDecimal unrealised = valuation.unrealisedPnl();

        return new PnlResponse(accountId, base, from, to, realised, unrealised, realised.add(unrealised),
            bySymbol ? breakdown(accountId, valuation, from, to) : null,
            clock.instant().truncatedTo(ChronoUnit.SECONDS));
    }

    // Every instrument that has realised profit or is held now, so a position sold out still shows.
    private List<SymbolPnlResponse> breakdown(long accountId, Valuation valuation, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> realised = new TreeMap<>(ledger.realisedBySymbol(accountId, valuation.baseCurrency(), from, to));
        Map<String, BigDecimal> unrealised = new TreeMap<>();
        for (Valuation.ValuedHolding holding : valuation.holdings()) {
            unrealised.put(holding.position().symbol(), holding.counted() ? holding.unrealisedBase() : zero());
        }

        Set<String> symbols = new TreeSet<>(realised.keySet());
        symbols.addAll(unrealised.keySet());

        List<SymbolPnlResponse> rows = new ArrayList<>();
        for (String symbol : symbols) {
            BigDecimal realisedPnl = realised.getOrDefault(symbol, zero());
            BigDecimal unrealisedPnl = unrealised.getOrDefault(symbol, zero());
            rows.add(new SymbolPnlResponse(symbol, realisedPnl, unrealisedPnl, realisedPnl.add(unrealisedPnl)));
        }
        return rows;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
