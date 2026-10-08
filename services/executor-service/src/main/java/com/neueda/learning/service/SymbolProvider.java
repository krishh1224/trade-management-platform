package com.neueda.learning.service;

import com.neueda.learning.mapper.PortfolioHoldingMapper;
import com.neueda.learning.mapper.StrategySymbolMapper;
import com.neueda.learning.mapper.WatchlistSymbolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that provides the set of symbols to poll for market data.
 * Sources symbols from portfolio holdings, watchlists and enabled automated strategies.
 */
@Service
public class SymbolProvider {

    private static final Logger logger = LoggerFactory.getLogger(SymbolProvider.class);

    private final PortfolioHoldingMapper holdingMapper;
    private final WatchlistSymbolMapper watchlistSymbolMapper;
    private final StrategySymbolMapper strategySymbolMapper;

    public SymbolProvider(
            PortfolioHoldingMapper holdingMapper,
            WatchlistSymbolMapper watchlistSymbolMapper,
            StrategySymbolMapper strategySymbolMapper) {
        this.holdingMapper = holdingMapper;
        this.watchlistSymbolMapper = watchlistSymbolMapper;
        this.strategySymbolMapper = strategySymbolMapper;
    }

    /**
    * Get all unique symbols that someone currently holds, has in watchlists, or trades with an
    * enabled strategy. Returns a deduplicated set of ticker symbols across all three sources.
     *
     * @return set of symbols to poll (e.g., {"INFY.NS", "RELIANCE.NS"})
     */
    public Set<String> getSymbolsToPoll() {
        var heldSymbols = holdingMapper.findAllHeldSymbols();
        var watchlistSymbols = watchlistSymbolMapper.findAllWatchlistSymbols();
        var strategySymbols = strategySymbols();

        Set<String> uniqueSymbols = new HashSet<>(heldSymbols);
        uniqueSymbols.addAll(watchlistSymbols);
        uniqueSymbols.addAll(strategySymbols);

        logger.debug(
            "Found {} unique symbols to poll (holdings={}, watchlists={}, strategies={})",
            uniqueSymbols.size(),
            heldSymbols.size(),
            watchlistSymbols.size(),
            strategySymbols.size()
        );

        return uniqueSymbols;
    }

    // A database without the strategies table (V18 not applied yet) must not stop the poller: holdings
    // and watchlists are still polled.
    private List<String> strategySymbols() {
        try {
            return strategySymbolMapper.findAllStrategySymbols();
        } catch (DataAccessException e) {
            logger.warn("Could not read strategy symbols, polling holdings and watchlists only: {}", e.getMessage());
            return List.of();
        }
    }
}
