package com.team5.advisor.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team5.advisor.data.AdvisorRepository;
import com.team5.advisor.data.AdvisorRepository.InstrumentRow;
import com.team5.advisor.tools.ToolViews.FxRateView;
import com.team5.advisor.tools.ToolViews.QuoteView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class AdvisorToolsTest {

    private final AdvisorRepository repository = mock(AdvisorRepository.class);
    private final AdvisorTools tools = new AdvisorTools(repository);
    private final ToolCallTracker tracker = new ToolCallTracker();
    private final ToolContext context = new ToolContext(Map.of(
        AdvisorTools.CTX_ACCOUNT_ID, 103L,
        AdvisorTools.CTX_BASE_CURRENCY, "INR",
        AdvisorTools.CTX_TRACKER, tracker));

    @Test
    void quotesNormaliseSymbolsCapAtTenAndFlagUnknownOnes() {
        when(repository.findInstruments(List.of("AAPL", "ZZZZ"))).thenReturn(List.of(
            new InstrumentRow("AAPL", "Apple Inc.", "NASDAQ", "USD", "EQUITY", true,
                new BigDecimal("333.73"), BigDecimal.ONE, LocalDateTime.parse("2026-10-04T15:00:00"), 12L)));

        List<QuoteView> quotes = tools.getQuotes(List.of(" aapl", "AAPL", "zzzz", ""), context);

        assertThat(quotes).extracting(QuoteView::symbol).containsExactly("AAPL", "ZZZZ");
        assertThat(quotes.get(0).priceAvailable()).isTrue();
        assertThat(quotes.get(0).priceAgeMinutes()).isEqualTo(12L);
        assertThat(quotes.get(1).priceAvailable()).isFalse();
        assertThat(quotes.get(1).name()).contains("Not an instrument");
        assertThat(tracker.names()).containsExactly("get_quotes");

        List<String> many = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");
        assertThat(AdvisorTools.normaliseSymbols(many)).hasSize(AdvisorTools.MAX_QUOTE_SYMBOLS);
    }

    @Test
    void fxRateForTheBaseCurrencyIsOneWithoutAQuery() {
        FxRateView view = tools.getFxRate("inr", context);

        assertThat(view.available()).isTrue();
        assertThat(view.rate()).isEqualByComparingTo("1");
        verify(repository, never()).latestSettledFxRate(anyString(), anyString());
    }

    @Test
    void fxRateReportsWhenNoOrderHasSettledInThatCurrency() {
        when(repository.latestSettledFxRate("USD", "INR")).thenReturn(Optional.empty());

        FxRateView view = tools.getFxRate("usd", context);

        assertThat(view.available()).isFalse();
        assertThat(view.rate()).isNull();
    }

    @Test
    void orderHistoryUsesTheAccountFromContextAndClampsTheLimit() {
        when(repository.findRecentOrders(103L, 50)).thenReturn(List.of());
        when(repository.findRecentOrders(103L, 10)).thenReturn(List.of());

        tools.getOrderHistory(500, context);
        tools.getOrderHistory(null, context);

        verify(repository).findRecentOrders(103L, 50);
        verify(repository).findRecentOrders(103L, 10);
    }

    @Test
    void blankSearchReturnsNothingWithoutAQuery() {
        assertThat(tools.searchInstruments("  ", context)).isEmpty();
        verify(repository, never()).searchInstruments(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }
}
