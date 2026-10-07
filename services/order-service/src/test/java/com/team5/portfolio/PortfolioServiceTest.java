package com.team5.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.PositionResponse;
import com.team5.portfolio.dto.PortfolioSummaryResponse;
import com.team5.portfolio.dto.PricedPositionResponse;
import com.team5.portfolio.exception.PortfolioAccessDeniedException;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.portfolio.pnl.RealisedPnlProvider;
import com.team5.portfolio.pricing.PricedQuote;
import com.team5.portfolio.pricing.PricingService;
import com.team5.portfolio.read.InstrumentCurrency;
import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PortfolioServiceTest {

    private static final long ACCOUNT = 1L;
    private static final Instant NOW = Instant.parse("2026-10-06T15:00:00.123Z");
    private static final Instant OBSERVED = Instant.parse("2026-10-06T14:47:09Z");

    private final AccountService accountService = Mockito.mock(AccountService.class);
    private final PricingService pricingService = Mockito.mock(PricingService.class);
    private final PortfolioReadMapper readMapper = Mockito.mock(PortfolioReadMapper.class);
    private final RealisedPnlProvider realised = Mockito.mock(RealisedPnlProvider.class);
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(accountService, new PortfolioAccessGuard(new AccountAccessAuthorizer()), pricingService, readMapper,
            realised, Clock.fixed(NOW, ZoneOffset.UTC));
        when(accountService.getBalance(ACCOUNT, ACCOUNT)).thenReturn(balance("1000.00", "INR"));
        when(realised.lifetimeRealised(ACCOUNT, "INR")).thenReturn(new BigDecimal("1145.50"));
    }

    @Test
    void positionIsPricedFromTheLiveQuote() {
        holdings(position("AAPL", 200, "228.40"));
        currencies(new InstrumentCurrency("AAPL", "USD"));
        when(pricingService.price(anySet())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));

        PricedPositionResponse p = service.getPositions(ACCOUNT, ACCOUNT, null).get(0);

        assertEquals(new BigDecimal("45680.00"), p.costBasis());
        assertEquals(new BigDecimal("46542.00"), p.marketValue());
        assertEquals(new BigDecimal("862.00"), p.unrealisedPnl());
        assertEquals(new BigDecimal("1.89"), p.unrealisedPnlPercent());
        assertEquals("USD", p.currency());
        assertEquals(OBSERVED, p.priceAsOf());
        assertFalse(p.stale());
    }

    @Test
    void aStaleQuoteIsServedAndMarked() {
        holdings(position("AAPL", 10, "100.00"));
        currencies(new InstrumentCurrency("AAPL", "USD"));
        when(pricingService.price(anySet())).thenReturn(Map.of("AAPL", quote("AAPL", "110.00", "USD", true)));

        PricedPositionResponse p = service.getPositions(ACCOUNT, ACCOUNT, null).get(0);

        assertEquals(new BigDecimal("1100.00"), p.marketValue());
        assertTrue(p.stale());
    }

    @Test
    void positionsAreFilteredBySymbolAndOnlyThatSymbolIsPriced() {
        holdings(position("AAPL", 10, "100.00"), position("MSFT", 5, "300.00"));
        currencies(new InstrumentCurrency("MSFT", "USD"));
        when(pricingService.price(anySet())).thenReturn(Map.of("MSFT", quote("MSFT", "310.00", "USD", false)));

        List<PricedPositionResponse> result = service.getPositions(ACCOUNT, ACCOUNT, "MSFT");

        assertEquals(1, result.size());
        assertEquals("MSFT", result.get(0).symbol());
        verify(pricingService).price(java.util.Set.of("MSFT"));
    }

    @Test
    void pricingUnavailableForEverySymbolIsTheDocumentedServiceUnavailableError() {
        holdings(position("AAPL", 10, "100.00"), position("INFY.NS", 5, "1500.00"));
        currencies(new InstrumentCurrency("AAPL", "USD"), new InstrumentCurrency("INFY.NS", "INR"));
        when(pricingService.price(anySet())).thenReturn(Map.of());

        assertThrows(PricingUnavailableException.class, () -> service.getPositions(ACCOUNT, ACCOUNT, null));
        assertThrows(PricingUnavailableException.class, () -> service.getSummary(ACCOUNT, ACCOUNT));
    }

    @Test
    void pricingUnavailableForSomeSymbolsIsAPartialAnswer() {
        holdings(position("AAPL", 10, "100.00"), position("INFY.NS", 40, "1580.25"));
        currencies(new InstrumentCurrency("AAPL", "USD"), new InstrumentCurrency("INFY.NS", "INR"));
        when(pricingService.price(anySet())).thenReturn(Map.of(
            "AAPL", quote("AAPL", "110.00", "USD", false),
            "FX:USDINR", quote("FX:USDINR", "90.00", "INR", false)));

        List<PricedPositionResponse> positions = service.getPositions(ACCOUNT, ACCOUNT, null);
        PricedPositionResponse unpriced = positions.get(1);
        assertNull(unpriced.lastPrice());
        assertNull(unpriced.marketValue());
        assertNull(unpriced.unrealisedPnl());
        assertNull(unpriced.unrealisedPnlPercent());
        assertNull(unpriced.priceAsOf());
        assertTrue(unpriced.stale());
        assertEquals(new BigDecimal("63210.00"), unpriced.costBasis());

        PortfolioSummaryResponse summary = service.getSummary(ACCOUNT, ACCOUNT);
        assertTrue(summary.partial());
        assertEquals(2, summary.positionCount());
        // Totals cover the priced holding only, so marketValue - costBasis stays coherent.
        assertEquals(new BigDecimal("99000.00"), summary.marketValue());
        assertEquals(new BigDecimal("90000.00"), summary.costBasis());
        assertEquals(new BigDecimal("9000.00"), summary.unrealisedPnl());
    }

    @Test
    void summaryConvertsForeignHoldingsIntoTheBaseCurrency() {
        holdings(position("AAPL", 200, "228.40"), position("INFY.NS", 40, "1580.25"));
        currencies(new InstrumentCurrency("AAPL", "USD"), new InstrumentCurrency("INFY.NS", "INR"));
        when(pricingService.price(anySet())).thenReturn(Map.of(
            "AAPL", quote("AAPL", "232.71", "USD", false),
            "INFY.NS", quote("INFY.NS", "1600.00", "INR", false),
            "FX:USDINR", quote("FX:USDINR", "90.00", "INR", false)));

        PortfolioSummaryResponse s = service.getSummary(ACCOUNT, ACCOUNT);

        // 46542.00 * 90 + 64000.00 = 4,252,780.00 ; cost 45680 * 90 + 63210 = 4,174,410.00
        assertEquals(new BigDecimal("4252780.00"), s.marketValue());
        assertEquals(new BigDecimal("4174410.00"), s.costBasis());
        assertEquals(new BigDecimal("78370.00"), s.unrealisedPnl());
        assertEquals(new BigDecimal("1.88"), s.unrealisedPnlPercent());
        assertEquals(new BigDecimal("4253780.00"), s.totalValue());
        assertEquals("INR", s.baseCurrency());
        assertFalse(s.partial());
        assertEquals(Instant.parse("2026-10-06T15:00:00Z"), s.asOf());
    }

    @Test
    void aCurrencyThatCannotBeConvertedMakesTheSummaryPartialInsteadOfFailing() {
        holdings(position("AAPL", 10, "100.00"), position("INFY.NS", 40, "1580.25"));
        currencies(new InstrumentCurrency("AAPL", "USD"), new InstrumentCurrency("INFY.NS", "INR"));
        when(pricingService.price(anySet())).thenReturn(Map.of(
            "AAPL", quote("AAPL", "110.00", "USD", false),
            "INFY.NS", quote("INFY.NS", "1600.00", "INR", false)));

        PortfolioSummaryResponse s = service.getSummary(ACCOUNT, ACCOUNT);

        assertTrue(s.partial());
        assertEquals(new BigDecimal("64000.00"), s.marketValue());
        assertEquals(new BigDecimal("63210.00"), s.costBasis());
    }

    @Test
    void realisedProfitAndLossIsPassedThroughNotRecomputedFromThePrice() {
        holdings(position("AAPL", 10, "100.00"));
        currencies(new InstrumentCurrency("AAPL", "INR"));
        when(pricingService.price(anySet())).thenReturn(Map.of("AAPL", quote("AAPL", "999.00", "INR", false)));

        assertEquals(new BigDecimal("1145.50"), service.getSummary(ACCOUNT, ACCOUNT).realisedPnl());
    }

    @Test
    void noHoldingsIsAnEmptyPortfolioAndNeverCallsThePricingFeed() {
        holdings();

        PortfolioSummaryResponse s = service.getSummary(ACCOUNT, ACCOUNT);

        assertEquals(new BigDecimal("0.00"), s.marketValue());
        assertEquals(new BigDecimal("1000.00"), s.totalValue());
        assertNull(s.unrealisedPnlPercent());
        assertEquals(0, s.positionCount());
        assertFalse(s.partial());
        assertTrue(service.getPositions(ACCOUNT, ACCOUNT, null).isEmpty());
        verifyNoInteractions(pricingService);
    }

    @Test
    void oneAccountCannotReadAnotherAccountsPortfolio() {
        assertThrows(PortfolioAccessDeniedException.class, () -> service.getSummary(2L, ACCOUNT));
        assertThrows(PortfolioAccessDeniedException.class, () -> service.getPositions(2L, ACCOUNT, null));

        verifyNoInteractions(accountService, pricingService, readMapper, realised);
    }

    @Test
    void zeroQuantityPositionsAreExcluded() {
        holdings(position("AAPL", 0, "100.00"));

        assertTrue(service.getPositions(ACCOUNT, ACCOUNT, null).isEmpty());
        verify(pricingService, never()).price(anySet());
    }

    private void holdings(PositionResponse... positions) {
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(List.of(positions));
    }

    private void currencies(InstrumentCurrency... currencies) {
        when(readMapper.findCurrencies(anyCollection())).thenReturn(List.of(currencies));
    }

    private static PositionResponse position(String symbol, int quantity, String averageCost) {
        return new PositionResponse(ACCOUNT, symbol, quantity, new BigDecimal(averageCost));
    }

    private static BalanceResponse balance(String cash, String currency) {
        return new BalanceResponse(ACCOUNT, new BigDecimal(cash), BigDecimal.ZERO, new BigDecimal(cash), currency,
            null);
    }

    private static PricedQuote quote(String symbol, String price, String currency, boolean stale) {
        return new PricedQuote(symbol, new BigDecimal(price), currency, OBSERVED, stale);
    }
}
