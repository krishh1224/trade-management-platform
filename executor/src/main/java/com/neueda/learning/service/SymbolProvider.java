package com.neueda.learning.service;

import com.neueda.learning.mapper.PortfolioHoldingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Service that provides the set of symbols to poll for market data.
 * Currently sources symbols from portfolio holdings.
 * Future: May extend to support watchlists if they are added.
 */
@Service
public class SymbolProvider {

    private static final Logger logger = LoggerFactory.getLogger(SymbolProvider.class);

    private final PortfolioHoldingMapper holdingMapper;

    public SymbolProvider(PortfolioHoldingMapper holdingMapper) {
        this.holdingMapper = holdingMapper;
    }

    /**
     * Get all unique symbols that someone currently holds.
     * Returns a deduplicated set of ticker symbols.
     *
     * @return set of symbols to poll (e.g., {"INFY.NS", "RELIANCE.NS"})
     */
    public Set<String> getSymbolsToPoll() {
        // Query database for all held symbols
        var symbols = holdingMapper.findAllHeldSymbols();

        // Deduplicate (should already be unique from SQL, but defensive)
        Set<String> uniqueSymbols = new HashSet<>(symbols);

        logger.debug("Found {} unique symbols to poll", uniqueSymbols.size());

        return uniqueSymbols;
    }
}
