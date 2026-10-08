package com.team5.strategies.engine;

import com.team5.strategies.config.StrategyProperties;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.engine.StrategyQuoteRecorder.Recorded;
import com.team5.strategies.indicators.StrategySignals;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One evaluation cycle: a quote arrives, every ENABLED strategy on its symbol is evaluated against the
 * symbol's recent prices, and each one whose rule fires is handed to {@link StrategyExecutor}, one at a
 * time, each in its own transaction. The list loaded here only decides what is evaluated; whether an
 * order may be placed is decided again by the executor, under the strategy's row lock.
 */
@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    private final StrategyQuoteRecorder quoteRecorder;
    private final StrategyQuoteMapper quoteMapper;
    private final StrategyMapper strategyMapper;
    private final StrategyExecutor executor;
    private final Duration decisionDelay;
    private final Duration maxQuoteAge;
    private final Clock clock;

    public StrategyEngine(
        StrategyQuoteRecorder quoteRecorder,
        StrategyQuoteMapper quoteMapper,
        StrategyMapper strategyMapper,
        StrategyExecutor executor,
        StrategyProperties properties,
        Clock clock
    ) {
        this.quoteRecorder = quoteRecorder;
        this.quoteMapper = quoteMapper;
        this.strategyMapper = strategyMapper;
        this.executor = executor;
        this.decisionDelay = properties.decisionDelay();
        this.maxQuoteAge = properties.maxQuoteAge();
        this.clock = clock;
    }

    public CycleResult onQuote(StrategyQuote quote) {
        Recorded recorded = quoteRecorder.record(quote);
        if (recorded == Recorded.STALE) {
            return new CycleResult(recorded, 0, 0, 0, false);
        }
        // a backlog read after downtime: the price belongs in the history, but it is not today's price
        if (quote.quotedAt().isBefore(OffsetDateTime.now(clock).minus(maxQuoteAge))) {
            log.info("Quote {} {} from {} is older than {}: stored for history, not traded on",
                quote.symbol(), quote.price(), quote.quotedAt(), maxQuoteAge);
            return new CycleResult(recorded, 0, 0, 0, true);
        }

        List<StrategyRow> strategies = strategyMapper.findEnabledBySymbol(quote.symbol());
        if (strategies.isEmpty()) {
            return new CycleResult(recorded, 0, 0, 0, false);
        }

        int needed = strategies.stream().mapToInt(StrategySignals::requiredPrices).max().orElse(0);
        List<BigDecimal> prices = new ArrayList<>(quoteMapper.findRecentPrices(quote.symbol(), quote.quotedAt(), needed));
        Collections.reverse(prices);

        int fired = 0;
        int placed = 0;
        for (StrategyRow strategy : strategies) {
            SignalResult signal = StrategySignals.evaluate(strategy, prices);
            if (!signal.fired()) {
                log.debug("Strategy {} on {} {}: {}", strategy.getStrategyId(), quote.symbol(), quote.price(), signal.reason());
                continue;
            }
            fired++;
            log.info("Strategy {} ({} on {}) fired {} at {}: {}", strategy.getStrategyId(), strategy.getStrategyType(),
                quote.symbol(), signal.side(), quote.price(), signal.reason());

            pauseBeforeDecision(strategy);
            ExecutionRow execution = executor.execute(strategy.getStrategyId(), signal, quote);
            if (execution != null && execution.getOutcome() == ExecutionOutcome.PLACED) {
                placed++;
            }
        }
        return new CycleResult(recorded, strategies.size(), fired, placed, false);
    }

    // Zero unless configured: a demonstration's window to disable a strategy between trigger and order
    private void pauseBeforeDecision(StrategyRow strategy) {
        if (decisionDelay.isZero()) {
            return;
        }
        log.info("Strategy {}: deciding in {} ms", strategy.getStrategyId(), decisionDelay.toMillis());
        try {
            Thread.sleep(decisionDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // shutting down: leave the quote unacknowledged so it is evaluated again after the restart
            throw new IllegalStateException("Interrupted before deciding strategy " + strategy.getStrategyId(), e);
        }
    }

    public record CycleResult(Recorded recorded, int evaluated, int fired, int placed, boolean tooOldToTrade) {
    }
}
