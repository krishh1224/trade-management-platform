package com.team5.portfolio.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.portfolio.config.FauxnanceProperties;
import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.portfolio.read.SnapshotPrice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PricingServiceTest {

    private static final Instant OBSERVED = Instant.parse("2026-10-06T14:47:09Z");

    private final FauxnanceClient client = Mockito.mock(FauxnanceClient.class);
    private final PortfolioReadMapper readMapper = Mockito.mock(PortfolioReadMapper.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-06T15:00:00Z"));
    private PricingService pricing;

    @BeforeEach
    void setUp() {
        FauxnanceProperties properties = new FauxnanceProperties("http://fauxnance", "key",
            Duration.ofSeconds(60), Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofMinutes(5), Duration.ofHours(1));
        pricing = new PricingService(client, new QuoteCache(properties, clock), readMapper);
    }

    @Test
    void positionsArePricedFromALiveQuote() {
        when(client.fetch(anyCollection())).thenReturn(Map.of("AAPL", quote("AAPL", "333.59", false)));

        Map<String, PricedQuote> result = pricing.price(Set.of("AAPL"));

        assertEquals(new BigDecimal("333.59"), result.get("AAPL").price());
        assertEquals(OBSERVED, result.get("AAPL").asOf());
        assertFalse(result.get("AAPL").stale());
    }

    @Test
    void aCachedQuoteIsReusedWithinTheCacheWindow() {
        when(client.fetch(anyCollection())).thenReturn(Map.of("AAPL", quote("AAPL", "333.59", false)));

        pricing.price(Set.of("AAPL"));
        clock.advance(Duration.ofSeconds(59));
        pricing.price(Set.of("AAPL"));

        verify(client, times(1)).fetch(anyCollection());
    }

    @Test
    void anExpiredQuoteIsFetchedAgain() {
        when(client.fetch(anyCollection())).thenReturn(Map.of("AAPL", quote("AAPL", "333.59", false)));

        pricing.price(Set.of("AAPL"));
        clock.advance(Duration.ofSeconds(61));
        pricing.price(Set.of("AAPL"));

        verify(client, times(2)).fetch(anyCollection());
    }

    @Test
    void tenHoldingsCostOneCallAndThirtyCostTwo() {
        when(client.fetch(anyCollection())).thenReturn(Map.of());

        pricing.price(symbols(10));
        verify(client, times(1)).fetch(anyCollection());

        Mockito.clearInvocations(client);
        pricing.price(symbols(30));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> batches = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(client, times(2)).fetch(batches.capture());
        assertEquals(25, batches.getAllValues().get(0).size());
        assertEquals(5, batches.getAllValues().get(1).size());
    }

    @Test
    void aSymbolWithoutAQuoteIsAbsentFromTheResult() {
        when(client.fetch(anyCollection())).thenReturn(Map.of("AAPL", quote("AAPL", "333.59", false)));

        Map<String, PricedQuote> result = pricing.price(new LinkedHashSet<>(List.of("AAPL", "INFY.NS")));

        assertTrue(result.containsKey("AAPL"));
        assertFalse(result.containsKey("INFY.NS"));
    }

    @Test
    void upstreamStaleFlagIsPassedThrough() {
        when(client.fetch(anyCollection())).thenReturn(Map.of("AAPL", quote("AAPL", "333.59", true)));

        assertTrue(pricing.price(Set.of("AAPL")).get("AAPL").stale());
    }

    @Test
    void feedFailureServesTheLastKnownQuoteMarkedStale() {
        when(client.fetch(anyCollection()))
            .thenReturn(Map.of("AAPL", quote("AAPL", "333.59", false)))
            .thenThrow(new QuoteFeedException("down", null));

        pricing.price(Set.of("AAPL"));
        clock.advance(Duration.ofSeconds(120));
        PricedQuote served = pricing.price(Set.of("AAPL")).get("AAPL");

        assertEquals(new BigDecimal("333.59"), served.price());
        assertEquals(OBSERVED, served.asOf());
        assertTrue(served.stale());
    }

    @Test
    void feedFailureWithNoHistoryFallsBackToTheSnapshotMarkedStale() {
        when(client.fetch(anyCollection())).thenThrow(new QuoteFeedException("down", null));
        when(readMapper.findSnapshots(anyCollection())).thenReturn(List.of(new SnapshotPrice(
            "INFY.NS", new BigDecimal("1013.85"), "INR", LocalDateTime.ofInstant(OBSERVED, ZoneOffset.UTC))));

        PricedQuote served = pricing.price(Set.of("INFY.NS")).get("INFY.NS");

        assertEquals(new BigDecimal("1013.85"), served.price());
        assertEquals("INR", served.currency());
        assertTrue(served.stale());
    }

    @Test
    void fxPairsNeverFallBackToTheSnapshotTable() {
        when(client.fetch(anyCollection())).thenThrow(new QuoteFeedException("down", null));

        assertTrue(pricing.price(Set.of("FX:USDINR")).isEmpty());
        verify(readMapper, never()).findSnapshots(anyCollection());
    }

    @Test
    void anUnexpectedErrorFromTheFeedNeverEscapes() {
        when(client.fetch(anyCollection())).thenThrow(new IllegalStateException("boom"));
        when(readMapper.findSnapshots(anyCollection())).thenThrow(new IllegalStateException("db down"));

        assertTrue(pricing.price(Set.of("AAPL")).isEmpty());
    }

    @Test
    void nothingIsFetchedForNoSymbols() {
        assertTrue(pricing.price(Set.of()).isEmpty());
        verifyNoInteractions(client);
    }

    private static PricedQuote quote(String symbol, String price, boolean stale) {
        return new PricedQuote(symbol, new BigDecimal(price), "USD", OBSERVED, stale);
    }

    private static Set<String> symbols(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("SYM" + i);
        }
        return new LinkedHashSet<>(names);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
