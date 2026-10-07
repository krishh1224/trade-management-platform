package com.team5.portfolio.pnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team5.dto.response.PositionResponse;
import com.team5.portfolio.read.FilledOrder;
import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RealisedPnlLedgerTest {

    private static final long ACCOUNT = 106L;

    private final PortfolioReadMapper readMapper = Mockito.mock(PortfolioReadMapper.class);
    private final AccountService accountService = Mockito.mock(AccountService.class);
    private final InMemoryLedger ledger = new InMemoryLedger();
    private RealisedPnlLedger realised;

    @BeforeEach
    void setUp() {
        realised = new RealisedPnlLedger(readMapper, ledger.mapper(), accountService);
        // account 106 in the seed data: 80 HDFCBANK held at 1600, one sale of 10 at 1700
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(
            List.of(new PositionResponse(ACCOUNT, "HDFCBANK.NS", 80, new BigDecimal("1600.0000"))));
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(513, "HDFCBANK.NS", "INR", "BUY", 20, "1650", null, "2026-09-27T16:51:06"),
            order(514, "HDFCBANK.NS", "INR", "SELL", 10, "1700", null, "2026-09-27T16:51:06")));
    }

    @Test
    void aSaleIsBookedAtTheCostItHadWhenItWasMade() {
        BigDecimal total = realised.lifetimeRealised(ACCOUNT, "INR");

        // (1700 - 1600) * 10
        assertEquals(new BigDecimal("1000.00"), total);
        LedgerEntry entry = ledger.entries.get(514L);
        assertEquals(new BigDecimal("1600.0000"), entry.averageCostAtSale());
        assertEquals(new BigDecimal("1700.0000"), entry.fillPrice());
        assertEquals(10L, entry.quantitySold());
        assertEquals(BigDecimal.ONE, entry.fxRate());
    }

    @Test
    void aSaleInAForeignCurrencyIsConvertedAtTheRateItFilledAt() {
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(
            List.of(new PositionResponse(ACCOUNT, "AAPL", 3, new BigDecimal("300.0000"))));
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(1, "AAPL", "USD", "BUY", 5, "300", "96.00000000", "2026-10-01T10:00:00"),
            order(2, "AAPL", "USD", "SELL", 2, "330", "97.50000000", "2026-10-02T10:00:00")));

        BigDecimal total = realised.lifetimeRealised(ACCOUNT, "INR");

        // (330 - 300) * 2 = 60 USD, at the sale's rate of 97.5 = 5850.00 INR
        assertEquals(new BigDecimal("5850.00"), total);
        assertEquals(new BigDecimal("60.0000"), ledger.entries.get(2L).realisedPnl());
        assertEquals("USD", ledger.entries.get(2L).instrumentCurrency());
    }

    @Test
    void aLossIsBookedAsANegativeFigure() {
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(514, "HDFCBANK.NS", "INR", "SELL", 10, "1500", null, "2026-09-27T16:51:06")));

        assertEquals(new BigDecimal("-1000.00"), realised.lifetimeRealised(ACCOUNT, "INR"));
    }

    @Test
    void aReplayedSaleDoesNotDoubleCount() {
        realised.lifetimeRealised(ACCOUNT, "INR");
        BigDecimal again = realised.lifetimeRealised(ACCOUNT, "INR");
        int bookedByAThirdRun = realised.book(ACCOUNT, "INR");

        assertEquals(new BigDecimal("1000.00"), again);
        assertEquals(0, bookedByAThirdRun);
        assertEquals(1, ledger.entries.size());
    }

    @Test
    void theSameSaleSeenTwiceInOneRunIsStillOnlyBookedOnce() {
        // two requests racing: both read the ledger before either has written
        ledger.hideBookedFromReads = true;

        realised.book(ACCOUNT, "INR");
        realised.book(ACCOUNT, "INR");

        assertEquals(1, ledger.entries.size());
        assertEquals(2, ledger.insertAttempts);
        assertEquals(new BigDecimal("1000.00"), realised.lifetimeRealised(ACCOUNT, "INR"));
    }

    @Test
    void aBookedSaleIsNeverRecomputedEvenWhenTheHistoryNowImpliesAnotherCost() {
        realised.lifetimeRealised(ACCOUNT, "INR");
        BigDecimal booked = ledger.entries.get(514L).realisedPnlBase();

        // the holding changes later (more buys at a much higher cost): a recomputation would now differ
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(
            List.of(new PositionResponse(ACCOUNT, "HDFCBANK.NS", 200, new BigDecimal("2500.0000"))));

        assertEquals(booked, realised.lifetimeRealised(ACCOUNT, "INR"));
        assertEquals(1, ledger.insertAttempts);
    }

    @Test
    void realisedProfitDoesNotDependOnAnyPrice() {
        // the ledger has no price input at all: the figure is fixed by the fill and the cost at sale
        BigDecimal first = realised.lifetimeRealised(ACCOUNT, "INR");
        Mockito.reset(accountService);
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(
            List.of(new PositionResponse(ACCOUNT, "HDFCBANK.NS", 80, new BigDecimal("1600.0000"))));

        assertEquals(first, realised.lifetimeRealised(ACCOUNT, "INR"));
    }

    @Test
    void nothingIsReadForHoldingsWhenThereIsNothingNewToBook() {
        realised.lifetimeRealised(ACCOUNT, "INR");
        Mockito.clearInvocations(accountService);

        realised.lifetimeRealised(ACCOUNT, "INR");

        verify(accountService, never()).getPositions(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void aSaleWithNoRecordedRateForAForeignCurrencyIsLeftUnbookedNotGuessed() {
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(1, "AAPL", "USD", "BUY", 5, "300", null, "2026-10-01T10:00:00"),
            order(2, "AAPL", "USD", "SELL", 2, "330", null, "2026-10-02T10:00:00")));
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(
            List.of(new PositionResponse(ACCOUNT, "AAPL", 3, new BigDecimal("300.0000"))));

        assertEquals(new BigDecimal("0.00"), realised.lifetimeRealised(ACCOUNT, "INR"));
        assertTrue(ledger.entries.isEmpty());
    }

    @Test
    void aSaleTheOrderHistoryCannotExplainIsLeftUnbooked() {
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(List.of());
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(9, "ITC.NS", "INR", "SELL", 5, "450", null, "2026-09-27T16:51:06")));

        assertEquals(new BigDecimal("0.00"), realised.lifetimeRealised(ACCOUNT, "INR"));
        assertTrue(ledger.entries.isEmpty());
    }

    @Test
    void buysAloneBookNothingAndReadNothingMore() {
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(1, "ITC.NS", "INR", "BUY", 5, "450", null, "2026-09-27T16:51:06")));

        assertEquals(new BigDecimal("0.00"), realised.lifetimeRealised(ACCOUNT, "INR"));
        verify(accountService, never()).getPositions(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void theDateRangeIsInclusiveOfBothEndsAndPassedToTheLedger() {
        realised.realised(ACCOUNT, "INR", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        assertEquals(LocalDateTime.parse("2026-09-01T00:00:00"), ledger.lastFrom);
        // the last day is included, so the exclusive bound is the start of the next day
        assertEquals(LocalDateTime.parse("2026-10-01T00:00:00"), ledger.lastTo);
    }

    @Test
    void anUnboundedRangeSendsNoBounds() {
        realised.lifetimeRealised(ACCOUNT, "INR");

        assertEquals(null, ledger.lastFrom);
        assertEquals(null, ledger.lastTo);
    }

    @Test
    void theBreakdownIsPerInstrumentAndOrderedBySymbol() {
        when(accountService.getPositions(ACCOUNT, ACCOUNT)).thenReturn(List.of(
            new PositionResponse(ACCOUNT, "HDFCBANK.NS", 80, new BigDecimal("1600.0000")),
            new PositionResponse(ACCOUNT, "ITC.NS", 30, new BigDecimal("450.0000"))));
        when(readMapper.findFilledOrders(ACCOUNT)).thenReturn(List.of(
            order(514, "HDFCBANK.NS", "INR", "SELL", 10, "1700", null, "2026-09-27T16:51:06"),
            order(515, "ITC.NS", "INR", "SELL", 10, "400", null, "2026-09-28T09:00:00")));

        Map<String, BigDecimal> bySymbol = realised.realisedBySymbol(ACCOUNT, "INR", null, null);

        assertEquals(List.of("HDFCBANK.NS", "ITC.NS"), new ArrayList<>(bySymbol.keySet()));
        assertEquals(new BigDecimal("1000.00"), bySymbol.get("HDFCBANK.NS"));
        assertEquals(new BigDecimal("-500.00"), bySymbol.get("ITC.NS"));
    }

    private static FilledOrder order(long id, String symbol, String currency, String side, long quantity, String price,
                                     String fxRate, String executedAt) {
        return new FilledOrder(id, symbol, currency, side, quantity, new BigDecimal(price).setScale(4),
            fxRate == null ? null : new BigDecimal(fxRate), LocalDateTime.parse(executedAt));
    }

    /** A stand-in for the ledger table with the same primary-key behaviour: a second insert of an order id adds nothing. */
    private static final class InMemoryLedger {
        final Map<Long, LedgerEntry> entries = new LinkedHashMap<>();
        int insertAttempts;
        boolean hideBookedFromReads;
        LocalDateTime lastFrom;
        LocalDateTime lastTo;

        PnlLedgerMapper mapper() {
            PnlLedgerMapper mapper = Mockito.mock(PnlLedgerMapper.class);
            when(mapper.insertIfAbsent(any())).thenAnswer(invocation -> {
                insertAttempts++;
                LedgerEntry entry = invocation.getArgument(0);
                return entries.putIfAbsent(entry.orderId(), entry) == null ? 1 : 0;
            });
            when(mapper.findBookedOrderIds(Mockito.anyLong())).thenAnswer(
                invocation -> hideBookedFromReads ? List.<Long>of() : new ArrayList<>(entries.keySet()));
            when(mapper.sumRealised(Mockito.anyLong(), any(), any())).thenAnswer(invocation -> {
                lastFrom = invocation.getArgument(1);
                lastTo = invocation.getArgument(2);
                return entries.values().stream().map(LedgerEntry::realisedPnlBase).reduce(BigDecimal.ZERO, BigDecimal::add);
            });
            when(mapper.sumRealisedBySymbol(Mockito.anyLong(), any(), any())).thenAnswer(invocation -> {
                Map<String, BigDecimal> sums = new java.util.TreeMap<>();
                entries.values().forEach(e -> sums.merge(e.symbol(), e.realisedPnlBase(), BigDecimal::add));
                return sums.entrySet().stream().map(e -> new SymbolRealised(e.getKey(), e.getValue())).toList();
            });
            return mapper;
        }
    }
}
