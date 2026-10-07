package com.team5.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.portfolio.dto.PnlResponse;
import com.team5.portfolio.dto.PricedPositionResponse;
import com.team5.portfolio.dto.SymbolPnlResponse;
import com.team5.portfolio.exception.InvalidInputException;
import com.team5.portfolio.exception.PortfolioAccessDeniedException;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.portfolio.pnl.RealisedPnlLedger;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PnlServiceTest {

    private static final long ACCOUNT = 103L;
    private static final Instant NOW = Instant.parse("2026-10-06T15:00:00.456Z");

    private final PortfolioService portfolioService = Mockito.mock(PortfolioService.class);
    private final RealisedPnlLedger ledger = Mockito.mock(RealisedPnlLedger.class);
    private PnlService service;

    @BeforeEach
    void setUp() {
        service = new PnlService(new PortfolioAccessGuard(new AccountAccessAuthorizer()), portfolioService, ledger,
            Clock.fixed(NOW, ZoneOffset.UTC));
        // AAPL: market value 4,188,780.00 on a cost of 4,111,200.00 -> +77,580.00 ; INFY.NS cannot be priced
        when(portfolioService.valuate(ACCOUNT, ACCOUNT)).thenReturn(new Valuation("INR", new BigDecimal("1000.00"), 2,
            List.of(
                holding("AAPL", "4188780.00", "4111200.00"),
                new Valuation.ValuedHolding(unpriced("INFY.NS"), null, null))));
        when(ledger.realised(eq(ACCOUNT), eq("INR"), any(), any())).thenReturn(new BigDecimal("1145.50"));
    }

    @Test
    void totalIsTheRealisedFigurePlusTheUnrealisedOne() {
        PnlResponse pnl = service.getPnl(ACCOUNT, ACCOUNT, null, null, false);

        assertEquals("INR", pnl.baseCurrency());
        assertEquals(new BigDecimal("1145.50"), pnl.realisedPnl());
        assertEquals(new BigDecimal("77580.00"), pnl.unrealisedPnl());
        assertEquals(new BigDecimal("78725.50"), pnl.totalPnl());
        assertEquals(Instant.parse("2026-10-06T15:00:00Z"), pnl.asOf());
    }

    @Test
    void unrealisedProfitIsTheSameFigureTheSummaryUses() {
        // both routes read it from the same valuation, so they cannot drift apart
        Valuation valuation = portfolioService.valuate(ACCOUNT, ACCOUNT);

        assertEquals(valuation.unrealisedPnl(), service.getPnl(ACCOUNT, ACCOUNT, null, null, false).unrealisedPnl());
    }

    @Test
    void theDateBoundsApplyToTheRealisedFigureOnly() {
        LocalDate from = LocalDate.parse("2026-09-01");
        LocalDate to = LocalDate.parse("2026-10-19");

        PnlResponse pnl = service.getPnl(ACCOUNT, ACCOUNT, from, to, false);

        verify(ledger).realised(ACCOUNT, "INR", from, to);
        assertEquals(from, pnl.from());
        assertEquals(to, pnl.to());
        assertEquals(new BigDecimal("77580.00"), pnl.unrealisedPnl());
    }

    @Test
    void anUnboundedRequestReportsNullBounds() {
        PnlResponse pnl = service.getPnl(ACCOUNT, ACCOUNT, null, null, false);

        assertNull(pnl.from());
        assertNull(pnl.to());
    }

    @Test
    void theBreakdownIsOmittedUnlessAskedFor() {
        assertNull(service.getPnl(ACCOUNT, ACCOUNT, null, null, false).bySymbol());
    }

    @Test
    void theBreakdownCoversHeldAndSoldOutInstrumentsInSymbolOrder() {
        Map<String, BigDecimal> realised = new LinkedHashMap<>();
        realised.put("AAPL", new BigDecimal("1000.00"));
        realised.put("ITC.NS", new BigDecimal("145.50"));
        when(ledger.realisedBySymbol(eq(ACCOUNT), eq("INR"), any(), any())).thenReturn(realised);

        List<SymbolPnlResponse> rows = service.getPnl(ACCOUNT, ACCOUNT, null, null, true).bySymbol();

        assertEquals(List.of("AAPL", "INFY.NS", "ITC.NS"), rows.stream().map(SymbolPnlResponse::symbol).toList());
        SymbolPnlResponse aapl = rows.get(0);
        assertEquals(new BigDecimal("1000.00"), aapl.realisedPnl());
        assertEquals(new BigDecimal("77580.00"), aapl.unrealisedPnl());
        assertEquals(new BigDecimal("78580.00"), aapl.totalPnl());
        // held but unpriced: no unrealised figure to add, and it is left out of the total like the summary
        assertEquals(new BigDecimal("0.00"), rows.get(1).unrealisedPnl());
        // sold out: realised only
        assertEquals(new BigDecimal("145.50"), rows.get(2).realisedPnl());
        assertEquals(new BigDecimal("0.00"), rows.get(2).unrealisedPnl());
    }

    @Test
    void aFromLaterThanToIsRejectedBeforeAnythingIsRead() {
        assertThrows(InvalidInputException.class, () -> service.getPnl(ACCOUNT, ACCOUNT,
            LocalDate.parse("2026-10-19"), LocalDate.parse("2026-09-01"), false));

        verifyNoInteractions(portfolioService, ledger);
    }

    @Test
    void aSingleDayRangeIsValid() {
        LocalDate day = LocalDate.parse("2026-10-01");

        service.getPnl(ACCOUNT, ACCOUNT, day, day, false);

        verify(ledger).realised(ACCOUNT, "INR", day, day);
    }

    @Test
    void pricingUnavailableForEveryHoldingIsTheDocumentedError() {
        when(portfolioService.valuate(ACCOUNT, ACCOUNT)).thenThrow(new PricingUnavailableException());

        assertThrows(PricingUnavailableException.class, () -> service.getPnl(ACCOUNT, ACCOUNT, null, null, false));
        verify(ledger, never()).realised(Mockito.anyLong(), any(), any(), any());
    }

    @Test
    void oneAccountCannotReadAnotherAccountsProfitAndLoss() {
        assertThrows(PortfolioAccessDeniedException.class, () -> service.getPnl(2L, ACCOUNT, null, null, true));

        verifyNoInteractions(portfolioService, ledger);
    }

    @Test
    void anAccountWithNothingHeldAndNothingSoldIsAllZero() {
        when(portfolioService.valuate(ACCOUNT, ACCOUNT)).thenReturn(new Valuation("INR", new BigDecimal("1000.00"), 0, List.of()));
        when(ledger.realised(eq(ACCOUNT), eq("INR"), any(), any())).thenReturn(new BigDecimal("0.00"));

        PnlResponse pnl = service.getPnl(ACCOUNT, ACCOUNT, null, null, true);

        assertEquals(new BigDecimal("0.00"), pnl.totalPnl());
        assertEquals(List.of(), pnl.bySymbol());
    }

    private static Valuation.ValuedHolding holding(String symbol, String marketValueBase, String costBasisBase) {
        return new Valuation.ValuedHolding(unpriced(symbol), new BigDecimal(marketValueBase), new BigDecimal(costBasisBase));
    }

    private static PricedPositionResponse unpriced(String symbol) {
        return new PricedPositionResponse(ACCOUNT, symbol, 1, BigDecimal.ONE, BigDecimal.ONE, null, null, null, null,
            "INR", null, true);
    }
}
