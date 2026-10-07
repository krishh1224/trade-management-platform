package com.team5.portfolio;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.PositionResponse;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.portfolio.candles.Candle;
import com.team5.portfolio.candles.CandleRange;
import com.team5.portfolio.candles.CandleService;
import com.team5.portfolio.candles.CandlesResponse;
import com.team5.portfolio.pnl.RealisedPnlProvider;
import com.team5.portfolio.pricing.PricedQuote;
import com.team5.portfolio.pricing.PricingService;
import com.team5.portfolio.read.InstrumentCurrency;
import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import java.time.ZoneOffset;

// Real PortfolioService and authorizer, mocked collaborators: proves the routes end to end.
@WebMvcTest(PortfolioController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class, PortfolioService.class, PortfolioAccessGuard.class, AccountAccessAuthorizer.class,
    PortfolioControllerTest.FixedClock.class})
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private PricingService pricingService;

    @MockBean
    private PortfolioReadMapper readMapper;

    @MockBean
    private RealisedPnlProvider realisedPnlProvider;

    @MockBean
    private CandleService candleService;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-06T15:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void summaryUsesTheContractFieldNames() throws Exception {
        arrangeOneHolding();
        when(pricingService.price(anySet())).thenReturn(Map.of("AAPL", quote("AAPL", "110.00", false)));
        when(realisedPnlProvider.lifetimeRealised(1L, "USD")).thenReturn(new BigDecimal("5.00"));

        mockMvc.perform(get("/api/v1/portfolio/{id}", 1L).with(authenticatedAs(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(1))
            .andExpect(jsonPath("$.baseCurrency").value("USD"))
            .andExpect(jsonPath("$.cashBalance").value(1000.0))
            .andExpect(jsonPath("$.marketValue").value(1100.0))
            .andExpect(jsonPath("$.costBasis").value(1000.0))
            .andExpect(jsonPath("$.unrealisedPnl").value(100.0))
            .andExpect(jsonPath("$.unrealisedPnlPercent").value(10.0))
            .andExpect(jsonPath("$.realisedPnl").value(5.0))
            .andExpect(jsonPath("$.totalValue").value(2100.0))
            .andExpect(jsonPath("$.positionCount").value(1))
            .andExpect(jsonPath("$.partial").value(false))
            .andExpect(jsonPath("$.asOf").exists());
    }

    @Test
    void positionsCarryTheTimeTheQuoteWasObservedAndWhetherItIsStale() throws Exception {
        arrangeOneHolding();
        when(pricingService.price(anySet())).thenReturn(Map.of("AAPL", quote("AAPL", "110.00", true)));

        mockMvc.perform(get("/api/v1/portfolio/{id}/positions", 1L).with(authenticatedAs(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$[0].lastPrice").value(110.0))
            .andExpect(jsonPath("$[0].priceAsOf").value("2026-10-06T14:47:09Z"))
            .andExpect(jsonPath("$[0].stale").value(true));
    }

    @Test
    void anUnpricedPositionSerialisesNullPriceFieldsAndStaleTrue() throws Exception {
        arrangeOneHolding();
        when(pricingService.price(anySet())).thenReturn(Map.of("AAPL", quote("AAPL", "110.00", false)));
        when(accountService.getPositions(1L, 1L)).thenReturn(List.of(
            new PositionResponse(1L, "AAPL", 10, new BigDecimal("100.00")),
            new PositionResponse(1L, "INFY.NS", 40, new BigDecimal("1580.25"))));
        when(readMapper.findCurrencies(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
            new InstrumentCurrency("AAPL", "USD"), new InstrumentCurrency("INFY.NS", "INR")));

        mockMvc.perform(get("/api/v1/portfolio/{id}/positions", 1L).with(authenticatedAs(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].lastPrice").value((Object) null))
            .andExpect(jsonPath("$[1].priceAsOf").value((Object) null))
            .andExpect(jsonPath("$[1].stale").value(true));
    }

    @Test
    void everyHeldSymbolUnpricedAnswers503WithTheDocumentedCode() throws Exception {
        arrangeOneHolding();
        when(pricingService.price(anySet())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/portfolio/{id}", 1L).with(authenticatedAs(1L)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("MKT-503"))
            .andExpect(jsonPath("$.message").value("Pricing unavailable"));
        mockMvc.perform(get("/api/v1/portfolio/{id}/positions", 1L).with(authenticatedAs(1L)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("MKT-503"));
    }

    @Test
    void anotherAccountsPortfolioIsForbiddenAndNothingIsRead() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/{id}", 2L).with(authenticatedAs(1L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"))
            .andExpect(jsonPath("$.message").value("Account not accessible"));
        mockMvc.perform(get("/api/v1/portfolio/{id}/positions", 2L).with(authenticatedAs(1L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(accountService, pricingService, readMapper);
    }

    @Test
    void candlesReturnTheSeriesForAHeldInstrument() throws Exception {
        when(candleService.getCandles(1L, 1L, "INFY.NS", CandleRange.SIX_MONTHS)).thenReturn(new CandlesResponse(
            "INFY.NS", "INR", "1d", "6M", false, Instant.parse("2026-10-06T15:00:00Z"),
            List.of(new Candle(java.time.LocalDate.parse("2026-10-06"), new BigDecimal("1018.0000"),
                new BigDecimal("1020.5000"), new BigDecimal("1003.1500"), new BigDecimal("1013.8500"), 8245427L,
                false))));

        mockMvc.perform(get("/api/v1/portfolio/{id}/candles/{symbol}", 1L, "INFY.NS")
                .param("range", "6M").with(authenticatedAs(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("INFY.NS"))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.range").value("6M"))
            .andExpect(jsonPath("$.stale").value(false))
            .andExpect(jsonPath("$.candles[0].date").value("2026-10-06"))
            .andExpect(jsonPath("$.candles[0].open").value(1018.0))
            .andExpect(jsonPath("$.candles[0].high").value(1020.5))
            .andExpect(jsonPath("$.candles[0].low").value(1003.15))
            .andExpect(jsonPath("$.candles[0].close").value(1013.85))
            .andExpect(jsonPath("$.candles[0].volume").value(8245427))
            .andExpect(jsonPath("$.candles[0].synthetic").value(false));
    }

    @Test
    void candlesDefaultToThreeMonths() throws Exception {
        when(candleService.getCandles(1L, 1L, "INFY.NS", CandleRange.THREE_MONTHS)).thenReturn(new CandlesResponse(
            "INFY.NS", "INR", "1d", "3M", false, Instant.parse("2026-10-06T15:00:00Z"), List.of()));

        mockMvc.perform(get("/api/v1/portfolio/{id}/candles/{symbol}", 1L, "INFY.NS").with(authenticatedAs(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range").value("3M"));
    }

    @Test
    void anUnknownCandleRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/{id}/candles/{symbol}", 1L, "INFY.NS")
                .param("range", "5Y").with(authenticatedAs(1L)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        verifyNoInteractions(candleService);
    }

    @Test
    void candlesForAnInstrumentTheAccountDoesNotHoldAre404() throws Exception {
        when(candleService.getCandles(1L, 1L, "AAPL", CandleRange.THREE_MONTHS))
            .thenThrow(new InstrumentNotFoundException());

        mockMvc.perform(get("/api/v1/portfolio/{id}/candles/{symbol}", 1L, "AAPL").with(authenticatedAs(1L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("INS-404"));
    }

    @Test
    void aMissingTokenIsUnauthorised() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/{id}", 1L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    @Test
    void aNonPositiveAccountIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/{id}", 0L).with(authenticatedAs(1L)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void aSymbolLongerThanTwentyCharactersIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/{id}/positions", 1L)
                .param("symbol", "A".repeat(21))
                .with(authenticatedAs(1L)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    private void arrangeOneHolding() {
        when(accountService.getBalance(1L, 1L)).thenReturn(new BalanceResponse(
            1L, new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"), "USD", null));
        when(accountService.getPositions(1L, 1L)).thenReturn(List.of(
            new PositionResponse(1L, "AAPL", 10, new BigDecimal("100.00"))));
        when(readMapper.findCurrencies(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(List.of(new InstrumentCurrency("AAPL", "USD")));
    }

    private static PricedQuote quote(String symbol, String price, boolean stale) {
        return new PricedQuote(symbol, new BigDecimal(price), "USD", Instant.parse("2026-10-06T14:47:09Z"), stale);
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token.claim("accountId", accountId));
    }
}
