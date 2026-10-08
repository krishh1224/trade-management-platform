package com.team5.portfolio.pricing;

import com.team5.portfolio.config.FauxnanceProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-instance quote cache shared by every account. An entry inside the TTL is served
 * without a call; an expired entry is kept as the last known value for use when the feed fails.
 */
@Component
public class QuoteCache {

    private record Entry(PricedQuote quote, Instant fetchedAt) {
    }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final FauxnanceProperties properties;
    private final Clock clock;

    public QuoteCache(FauxnanceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void put(PricedQuote quote) {
        entries.put(quote.symbol(), new Entry(quote, clock.instant()));
    }

    public Optional<PricedQuote> fresh(String symbol) {
        Entry entry = entries.get(symbol);
        if (entry == null || entry.fetchedAt().plus(properties.cacheTtl()).isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.quote());
    }

    public Optional<PricedQuote> lastKnown(String symbol) {
        return Optional.ofNullable(entries.get(symbol)).map(Entry::quote);
    }
}
