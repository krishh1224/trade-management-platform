package com.neueda.learning.service;

import com.neueda.learning.mapper.PortfolioHoldingMapper;
import com.neueda.learning.mapper.WatchlistSymbolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Service that provides the set of symbols to poll for market data.
 * Sources symbols from portfolio holdings and watchlists.
 */
@Service
public class SymbolProvider {

    private static final Logger logger = LoggerFactory.getLogger(SymbolProvider.class);

    private final PortfolioHoldingMapper holdingMapper;
    private final WatchlistSymbolMapper watchlistSymbolMapper;

    public SymbolProvider(PortfolioHoldingMapper holdingMapper, WatchlistSymbolMapper watchlistSymbolMapper) {
        this.holdingMapper = holdingMapper;
        this.watchlistSymbolMapper = watchlistSymbolMapper;
    }

    /**
    * Get all unique symbols that someone currently holds or has in watchlists.
    * Returns a deduplicated set of ticker symbols across both sources.
     *
     * @return set of symbols to poll (e.g., {"INFY.NS", "RELIANCE.NS"})
     */
    public Set<String> getSymbolsToPoll() {
        var heldSymbols = holdingMapper.findAllHeldSymbols();
        var watchlistSymbols = watchlistSymbolMapper.findAllWatchlistSymbols();

        Set<String> uniqueSymbols = new HashSet<>(heldSymbols);
        uniqueSymbols.addAll(watchlistSymbols);

        logger.debug(
            "Found {} unique symbols to poll (holdings={}, watchlists={})",
            uniqueSymbols.size(),
            heldSymbols.size(),
            watchlistSymbols.size()
        );

        return uniqueSymbols;
    }
}
