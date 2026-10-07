package com.team5.portfolio.candles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.dto.response.PositionResponse;
import com.team5.portfolio.PortfolioAccessGuard;
import com.team5.portfolio.config.FauxnanceProperties;
import com.team5.portfolio.exception.PortfolioAccessDeniedException;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.portfolio.pricing.FauxnanceClient;
import com.team5.portfolio.pricing.QuoteFeedException;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CandleServiceTest {

    private static final long ACCOUNT = 101L;
    private static final LocalDate LAST_DAY = LocalDate.parse("2026-10-06");

    private final AccountService accountService = Mockito.mock(AccountService.class);
    private final FauxnanceClient client = Mockito.mock(FauxnanceClient.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-06T15:00:00Z"));
    private CandleService service;

    @BeforeEach
    void setUp() {
        FauxnanceProperties properties = new FauxnanceProperties("http://fauxnance", "key", Duration.ofSeconds(60),
            Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofMinutes(5), Duration.ofHours(1));
        service = new CandleService(accountService, new PortfolioAccessGuard(new AccountAccessAuthorizer()), client,
            properties, clock);
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(
            List.of(new PositionResponse(ACCOUNT, "INFY.NS", 5, new BigDecimal("1000.00"))));
        when(client.candles("INFY.NS")).thenReturn(yearOfCandles());
    }

    @Test
    void returnsTheDailyCandlesForAHeldInstrument() {
        CandlesResponse response = service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.THREE_MONTHS);

        assertEquals("INFY.NS", response.symbol());
        assertEquals("INR", response.currency());
        assertEquals("1d", response.interval());
        assertEquals("3M", response.range());
        assertFalse(response.stale());
        assertEquals(Instant.parse("2026-10-06T15:00:00Z"), response.asOf());
        assertEquals(LAST_DAY, response.candles().get(response.candles().size() - 1).date());
    }

    @Test
    void eachRangeIsASliceEndingOnTheLatestCandle() {
        assertEquals(LocalDate.parse("2026-09-06"),
            service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.ONE_MONTH).candles().get(0).date());
        assertEquals(LocalDate.parse("2026-07-06"),
            service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.THREE_MONTHS).candles().get(0).date());
        assertEquals(LocalDate.parse("2026-04-06"),
            service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.SIX_MONTHS).candles().get(0).date());
        assertEquals(LocalDate.parse("2025-10-06"),
            service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.ONE_YEAR).candles().get(0).date());
    }

    @Test
    void oneFetchServesEveryRangeWithinTheCacheWindow() {
        for (CandleRange range : CandleRange.values()) {
            service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", range);
        }

        verify(client, times(1)).candles("INFY.NS");
    }

    @Test
    void anExpiredCopyIsFetchedAgain() {
        service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.ONE_MONTH);
        clock.advance(Duration.ofMinutes(61));
        service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.ONE_MONTH);

        verify(client, times(2)).candles("INFY.NS");
    }

    @Test
    void whenTheProviderFailsAnOlderCopyIsServedMarkedStale() {
        when(client.candles("INFY.NS")).thenReturn(yearOfCandles()).thenThrow(new QuoteFeedException("down", null));

        service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.ONE_MONTH);
        clock.advance(Duration.ofHours(2));
        CandlesResponse response = service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.ONE_MONTH);

        assertTrue(response.stale());
        assertEquals(Instant.parse("2026-10-06T15:00:00Z"), response.asOf());
        assertFalse(response.candles().isEmpty());
    }

    @Test
    void withNoCopyAtAllAProviderFailureIsTheDocumentedServiceUnavailableError() {
        when(client.candles("INFY.NS")).thenThrow(new QuoteFeedException("down", null));

        assertThrows(PricingUnavailableException.class,
            () -> service.getCandles(ACCOUNT, ACCOUNT, "INFY.NS", CandleRange.THREE_MONTHS));
    }

    @Test
    void aSymbolTheAccountDoesNotHoldIsNotFoundAndSpendsNoQuota() {
        assertThrows(InstrumentNotFoundException.class,
            () -> service.getCandles(ACCOUNT, ACCOUNT, "AAPL", CandleRange.THREE_MONTHS));

        verify(client, never()).candles(anyString());
    }

    @Test
    void oneAccountCannotReadCandlesThroughAnotherAccount() {
        assertThrows(PortfolioAccessDeniedException.class,
            () -> service.getCandles(2L, ACCOUNT, "INFY.NS", CandleRange.THREE_MONTHS));

        verifyNoInteractions(accountService, client);
    }

    private static CandleSeries yearOfCandles() {
        List<Candle> candles = new ArrayList<>();
        for (LocalDate day = LAST_DAY.minusYears(1); !day.isAfter(LAST_DAY); day = day.plusDays(1)) {
            candles.add(new Candle(day, new BigDecimal("100.0000"), new BigDecimal("105.0000"),
                new BigDecimal("99.0000"), new BigDecimal("103.0000"), 1000L, false));
        }
        return new CandleSeries("INFY.NS", "INR", candles);
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
