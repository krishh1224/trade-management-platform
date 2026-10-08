package com.team5.portfolio.candles;

import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.portfolio.PortfolioAccessGuard;
import com.team5.portfolio.config.FauxnanceProperties;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.portfolio.pricing.FauxnanceClient;
import com.team5.service.AccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily candles for an instrument the account holds. Fauxnance returns about a year of bars per call
 * and ignores any date window, so one fetch per symbol serves every range: it is cached for
 * {@code fauxnance.candle-cache-ttl} and sliced here. When the provider is down an older copy is
 * served marked stale; with no copy at all the answer is MKT-503.
 */
@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);
    private static final String INTERVAL = "1d";

    private record Cached(CandleSeries series, Instant fetchedAt) {
    }

    private final ConcurrentMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final AccountService accountService;
    private final PortfolioAccessGuard accessGuard;
    private final FauxnanceClient client;
    private final FauxnanceProperties properties;
    private final Clock clock;

    public CandleService(
        AccountService accountService,
        PortfolioAccessGuard accessGuard,
        FauxnanceClient client,
        FauxnanceProperties properties,
        Clock clock
    ) {
        this.accountService = accountService;
        this.accessGuard = accessGuard;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public CandlesResponse getCandles(long authenticatedAccountId, long accountId, String symbol, CandleRange range) {
        accessGuard.assertSameAccount(authenticatedAccountId, accountId);
        assertHeld(authenticatedAccountId, accountId, symbol);

        Instant now = clock.instant();
        Cached cached = cache.get(symbol);
        boolean stale = false;
        if (cached == null || cached.fetchedAt().plus(properties.candleCacheTtl()).isBefore(now)) {
            try {
                cached = new Cached(client.candles(symbol), now);
                cache.put(symbol, cached);
            } catch (RuntimeException e) {
                log.warn("Candle fetch failed for {}: {}", symbol, e.getMessage());
                if (cached == null) {
                    throw new PricingUnavailableException();
                }
                stale = true;
            }
        }

        return new CandlesResponse(symbol, cached.series().currency(), INTERVAL, range.code(), stale,
            cached.fetchedAt(), slice(cached.series().candles(), range));
    }

    // Only instruments the account holds, so the endpoint cannot be used to spend quota on any symbol.
    private void assertHeld(long authenticatedAccountId, long accountId, String symbol) {
        boolean held = accountService.getPositions(authenticatedAccountId, accountId).stream()
            .anyMatch(position -> position.symbol().equals(symbol) && position.quantity() != null
                && position.quantity() > 0);
        if (!held) {
            throw new InstrumentNotFoundException();
        }
    }

    private static List<Candle> slice(List<Candle> candles, CandleRange range) {
        if (candles.isEmpty()) {
            return candles;
        }
        LocalDate from = range.startingFrom(candles.get(candles.size() - 1).date());
        return candles.stream().filter(candle -> !candle.date().isBefore(from)).toList();
    }
}
