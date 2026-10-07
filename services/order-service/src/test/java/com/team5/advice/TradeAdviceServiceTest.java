package com.team5.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.AccountStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.PositionResponse;
import com.team5.dto.response.WatchlistItemResponse;
import com.team5.portfolio.PortfolioService;
import com.team5.portfolio.dto.PricedPositionResponse;
import com.team5.portfolio.exception.PricingUnavailableException;
import com.team5.service.AccountService;
import com.team5.service.WatchlistService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeAdviceServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private WatchlistService watchlistService;

    private TradeAdviceService tradeAdviceService;

    @BeforeEach
    void setUp() {
        tradeAdviceService = new TradeAdviceService(
            accountService,
            portfolioService,
            watchlistService,
            Clock.fixed(Instant.parse("2026-10-07T12:30:00Z"), ZoneOffset.UTC)
        );
        when(accountService.getAccount(101L, 101L)).thenReturn(new AccountResponse(
            101L,
            "ACC-101",
            "Team Five",
            new BigDecimal("100000.00"),
            AccountStatus.ACTIVE,
            1,
            OffsetDateTime.parse("2026-10-07T12:00:00Z")
        ));
    }

    @Test
    void signalGeneratedWithReasoning() {
        when(accountService.getPositions(101L, 101L)).thenReturn(List.of(
            new PositionResponse(101L, "INFY.NS", 10, new BigDecimal("1500.00"))
        ));
        when(portfolioService.getPositions(101L, 101L, null)).thenReturn(List.of(
            new PricedPositionResponse(101L, "INFY.NS", 10, new BigDecimal("1500.00"), new BigDecimal("15000.00"),
                new BigDecimal("1710.00"), new BigDecimal("17100.00"), new BigDecimal("2100.00"),
                new BigDecimal("14.00"), "INR", Instant.parse("2026-10-07T12:25:00Z"), false)
        ));
        when(watchlistService.listWatchlist(101L, null)).thenReturn(List.of());

        TradeAdviceResponse response = tradeAdviceService.getSignals(101L, 101L);

        assertEquals(1, response.signals().size());
        assertEquals("BUY", response.signals().get(0).signal());
        assertEquals("HOLDING", response.signals().get(0).origin());
        assertEquals(2, response.signals().get(0).reasoning().size());
        assertTrue(response.signals().get(0).reasoning().get(0).contains("+14.00%"));
        assertTrue(response.skipped().isEmpty());
    }

    @Test
    void noSignalWhenDataInsufficient() {
        when(accountService.getPositions(101L, 101L)).thenReturn(List.of(
            new PositionResponse(101L, "ITC.NS", 25, new BigDecimal("430.00"))
        ));
        when(portfolioService.getPositions(101L, 101L, null)).thenThrow(new PricingUnavailableException());
        when(watchlistService.listWatchlist(101L, null)).thenReturn(List.of(
            new WatchlistItemResponse(7001L, 5001L, "Favourite", 301L, "TCS.NS", "Tata Consultancy Services",
                OffsetDateTime.parse("2026-10-07T11:00:00Z"), null, null)
        ));

        TradeAdviceResponse response = tradeAdviceService.getSignals(101L, 101L);

        assertTrue(response.signals().isEmpty());
        assertEquals(2, response.skipped().size());
        assertEquals("ITC.NS", response.skipped().get(0).symbol());
        assertEquals("WATCHLIST", response.skipped().get(1).origin());
    }
}