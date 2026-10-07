package com.team5.portfolio.pricing;

import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.portfolio.read.SnapshotPrice;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prices a set of symbols with as few Fauxnance calls as possible: cache hits first, then the
 * misses in batches of 25, then fallbacks (last known value, executor snapshot) marked stale.
 * A symbol with no price anywhere is absent from the result.
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final FauxnanceClient client;
    private final QuoteCache cache;
    private final PortfolioReadMapper readMapper;

    public PricingService(FauxnanceClient client, QuoteCache cache, PortfolioReadMapper readMapper) {
        this.client = client;
        this.cache = cache;
        this.readMapper = readMapper;
    }

    public Map<String, PricedQuote> price(Set<String> symbols) {
        Map<String, PricedQuote> result = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        for (String symbol : new LinkedHashSet<>(symbols)) {
            cache.fresh(symbol).ifPresentOrElse(quote -> result.put(symbol, quote), () -> misses.add(symbol));
        }

        for (int from = 0; from < misses.size(); from += FauxnanceClient.MAX_SYMBOLS_PER_CALL) {
            List<String> batch = misses.subList(from,
                Math.min(from + FauxnanceClient.MAX_SYMBOLS_PER_CALL, misses.size()));
            try {
                client.fetch(batch).forEach((symbol, quote) -> {
                    cache.put(quote);
                    result.put(symbol, quote);
                });
            } catch (RuntimeException e) {
                // Pricing may fail; it must never fail the request path that called it.
                log.warn("Pricing call failed for {} symbols: {}", batch.size(), e.getMessage());
            }
        }

        applyFallbacks(misses.stream().filter(symbol -> !result.containsKey(symbol)).toList(), result);
        return result;
    }

    private void applyFallbacks(List<String> unpriced, Map<String, PricedQuote> result) {
        List<String> needSnapshot = new ArrayList<>();
        for (String symbol : unpriced) {
            cache.lastKnown(symbol).ifPresentOrElse(
                quote -> result.put(symbol, quote.asStale()),
                () -> {
                    // The snapshot table holds instruments only, never FX pairs.
                    if (!symbol.startsWith("FX:")) {
                        needSnapshot.add(symbol);
                    }
                });
        }
        if (needSnapshot.isEmpty()) {
            return;
        }
        try {
            for (SnapshotPrice snapshot : readMapper.findSnapshots(needSnapshot)) {
                if (snapshot.lastPrice() != null && snapshot.lastPrice().compareTo(BigDecimal.ZERO) > 0) {
                    result.put(snapshot.symbol(), new PricedQuote(snapshot.symbol(), snapshot.lastPrice(),
                        snapshot.currency(), snapshot.updatedAt().toInstant(ZoneOffset.UTC), true));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Snapshot fallback failed: {}", e.getMessage());
        }
    }
}
