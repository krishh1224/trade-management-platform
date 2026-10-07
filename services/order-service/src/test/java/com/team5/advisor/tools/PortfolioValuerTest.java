package com.team5.advisor.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.team5.advisor.data.AdvisorRepository.FxRow;
import com.team5.advisor.data.AdvisorRepository.HoldingRow;
import com.team5.advisor.tools.ToolViews.HoldingView;
import com.team5.advisor.tools.ToolViews.PortfolioView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PortfolioValuerTest {

    private static final LocalDateTime FX_AS_OF = LocalDateTime.parse("2026-10-04T15:28:00");

    private static HoldingRow holding(String symbol, String currency, long quantity, String avgCost, String lastPrice) {
        return new HoldingRow(symbol, symbol + " Ltd", "NSE", currency, quantity, new BigDecimal(avgCost),
            lastPrice == null ? null : new BigDecimal(lastPrice), BigDecimal.ONE,
            lastPrice == null ? null : LocalDateTime.parse("2026-10-04T15:00:00"), lastPrice == null ? null : 5L);
    }

    @Test
    void valuesInrAndUsdHoldingsInInrAtTheSettledRate() {
        PortfolioView view = PortfolioValuer.value("INR", new BigDecimal("1000.00"),
            List.of(
                holding("ITC.NS", "INR", 150, "430.0000", "255.94"),
                holding("AAPL", "USD", 2, "333.7300", "333.73")),
            (from, to) -> Optional.of(new FxRow(new BigDecimal("96.17096200"), FX_AS_OF)));

        HoldingView itc = view.holdings().get(0);
        HoldingView aapl = view.holdings().get(1);

        assertThat(itc.marketValue()).isEqualByComparingTo("38391.00");          // 150 x 255.94
        assertThat(itc.marketValueInBaseCurrency()).isEqualByComparingTo("38391.00");
        assertThat(itc.fxRateToBaseCurrency()).isEqualByComparingTo("1");
        assertThat(itc.unrealisedGainPercent()).isEqualByComparingTo("-40.48");  // (255.94-430)/430

        assertThat(aapl.marketValue()).isEqualByComparingTo("667.46");          // USD
        assertThat(aapl.marketValueInBaseCurrency()).isEqualByComparingTo("64190.27"); // 667.46 x 96.170962
        assertThat(aapl.fxRateAsOf()).isEqualTo(FX_AS_OF);

        assertThat(view.holdingsValueInBaseCurrency()).isEqualByComparingTo("102581.27");
        assertThat(view.totalValueInBaseCurrency()).isEqualByComparingTo("103581.27");
        assertThat(itc.shareOfHoldingsPercent()).isEqualByComparingTo("37.42");  // 38391 / 102581.27 = 37.42496%
        assertThat(aapl.shareOfHoldingsPercent()).isEqualByComparingTo("62.58");
        assertThat(view.notes()).anyMatch(note -> note.contains("approximate"));
    }

    @Test
    void leavesUnpricedAndUnconvertibleHoldingsOutOfTotalsAndSaysWhy() {
        PortfolioView view = PortfolioValuer.value("INR", new BigDecimal("500.00"),
            List.of(
                holding("INFY.NS", "INR", 10, "1000.0000", null),
                holding("NVDA", "USD", 1, "233.9800", "233.98")),
            (from, to) -> Optional.empty());

        assertThat(view.holdings().get(0).marketValueInBaseCurrency()).isNull();
        assertThat(view.holdings().get(1).marketValue()).isEqualByComparingTo("233.98");
        assertThat(view.holdings().get(1).marketValueInBaseCurrency()).isNull();
        assertThat(view.holdingsValueInBaseCurrency()).isEqualByComparingTo("0.00");
        assertThat(view.totalValueInBaseCurrency()).isEqualByComparingTo("500.00");
        assertThat(view.notes())
            .anyMatch(note -> note.startsWith("INFY.NS has no recent price"))
            .anyMatch(note -> note.startsWith("NVDA is quoted in USD"));
    }

    @Test
    void looksUpEachForeignCurrencyOnlyOnce() {
        AtomicInteger lookups = new AtomicInteger();
        PortfolioValuer.value("INR", BigDecimal.ZERO,
            List.of(holding("AAPL", "USD", 1, "1", "1"), holding("NVDA", "USD", 1, "1", "1")),
            (from, to) -> {
                lookups.incrementAndGet();
                return Optional.of(new FxRow(BigDecimal.TEN, FX_AS_OF));
            });

        assertThat(lookups).hasValue(1);
    }

    @Test
    void gainPercentIsNullWithoutACostOrPrice() {
        assertThat(PortfolioValuer.gainPercent(null, BigDecimal.ONE)).isNull();
        assertThat(PortfolioValuer.gainPercent(BigDecimal.ZERO, BigDecimal.ONE)).isNull();
        assertThat(PortfolioValuer.gainPercent(BigDecimal.ONE, null)).isNull();
    }
}
