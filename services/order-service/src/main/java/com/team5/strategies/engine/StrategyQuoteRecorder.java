package com.team5.strategies.engine;

import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.mapper.StrategyQuoteMapper;
import com.team5.strategies.mapper.StrategyQuoteMapper.StoredQuote;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends a quote to the symbol's price series, which every indicator is computed from.
 *
 * Only a quote newer than the newest stored one extends the series. An older one (a replay from an
 * earlier offset) is STALE and nothing is evaluated: acting on it would trade on yesterday's price as
 * if it were today's. The newest quote arriving again (a redelivery after a failure) is REDELIVERED and
 * is evaluated again; strategy_executions makes sure nothing it already did is done twice.
 */
@Component
public class StrategyQuoteRecorder {

    // the longest rule needs 202 prices (a 200-quote band window plus two); a few spare
    static final int KEEP_PER_SYMBOL = 250;

    public enum Recorded { NEW, REDELIVERED, STALE }

    private final StrategyQuoteMapper quoteMapper;

    public StrategyQuoteRecorder(StrategyQuoteMapper quoteMapper) {
        this.quoteMapper = quoteMapper;
    }

    @Transactional
    public Recorded record(StrategyQuote quote) {
        StoredQuote latest = quoteMapper.findLatest(quote.symbol());
        if (latest != null) {
            if (quote.quotedAt().isBefore(latest.getQuotedAt())) {
                return Recorded.STALE;
            }
            if (quote.quotedAt().isEqual(latest.getQuotedAt())) {
                return quote.eventId().equals(latest.getEventId()) ? Recorded.REDELIVERED : Recorded.STALE;
            }
        }
        quoteMapper.insert(quote.symbol(), quote.quotedAt(), quote.price(), quote.eventId());
        quoteMapper.prune(quote.symbol(), KEEP_PER_SYMBOL);
        return Recorded.NEW;
    }
}
