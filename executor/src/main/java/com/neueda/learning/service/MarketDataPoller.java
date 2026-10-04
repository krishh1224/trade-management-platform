package com.neueda.learning.service;

import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import com.neueda.learning.mapper.MarketQuoteSnapshotMapper;
import com.neueda.learning.producer.MarketDataPublisher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scheduled component that polls market data at a fixed interval.
 * 
 * Runs independently of order placement on a schedule.
 * Fetches quotes for symbols that someone holds or watches,
 * batches up to 25 symbols per HTTP request,
 * and publishes one Kafka message per quote.
 */
@Component
public class MarketDataPoller {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataPoller.class);

    private static final int MAX_BATCH_SIZE = 25;
    private static final long MIN_POLL_INTERVAL_SECONDS = 44;

    private final SymbolProvider symbolProvider;
    private final FauxnanceQuoteClient fauxnanceClient;
    private final MarketDataPublisher publisher;
    private final MarketQuoteSnapshotMapper marketQuoteSnapshotMapper;
    private final long pollIntervalSeconds;

    public MarketDataPoller(SymbolProvider symbolProvider,
                            FauxnanceQuoteClient fauxnanceClient,
                            MarketDataPublisher publisher,
                            MarketQuoteSnapshotMapper marketQuoteSnapshotMapper,
                            @Value("${market-data.poll-interval-seconds:600}") long pollIntervalSeconds) {
        this.symbolProvider = symbolProvider;
        this.fauxnanceClient = fauxnanceClient;
        this.publisher = publisher;
        this.marketQuoteSnapshotMapper = marketQuoteSnapshotMapper;
        this.pollIntervalSeconds = pollIntervalSeconds;

        // Validate interval at startup
        validatePollInterval();
    }

    /**
     * Scheduled task that polls market data.
     * Runs every POLL_INTERVAL_SECONDS.
     */
    @Scheduled(fixedRateString = "${market-data.poll-interval-seconds:600}", timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void pollMarketData() {
        try {
            logger.info("Market-data polling invocation started");

            // Get all symbols to poll
            Set<String> symbols = symbolProvider.getSymbolsToPoll();
            
            if (symbols.isEmpty()) {
                logger.info("No symbols to poll - empty holdings/watchlists");
                return;
            }

            logger.info("Polling {} unique symbols", symbols.size());

            // Batch symbols (max 25 per batch)
            List<List<String>> batches = batchSymbols(new ArrayList<>(symbols), MAX_BATCH_SIZE);
            logger.info("Batched into {} HTTP request(s)", batches.size());

            int successCount = 0;
            int failureCount = 0;

            // Process each batch
            for (List<String> batch : batches) {
                logger.debug("Processing batch of {} symbol(s): {}", batch.size(), batch);

                // Fetch quotes for each symbol in the batch
                for (String symbol : batch) {
                    try {
                        Quote quote = fauxnanceClient.getQuote(symbol);

                        BigDecimal lastPrice = midpoint(quote.getBid(), quote.getAsk());
                        BigDecimal previousLastPrice = marketQuoteSnapshotMapper.findLastPriceBySymbol(symbol);
                        BigDecimal changePct = computeChangePct(previousLastPrice, lastPrice);
                        marketQuoteSnapshotMapper.upsertSnapshot(symbol, lastPrice, changePct, OffsetDateTime.now());
                        
                        // Publish one Kafka message per quote (keyed by symbol)
                        publisher.publishQuote(symbol, quote);
                        successCount++;

                        logger.debug("Successfully fetched and published quote for {}", symbol);
                    } catch (QuoteUnavailableException e) {
                        failureCount++;
                        logger.warn("Failed to fetch quote for {}: {}", symbol, e.getMessage());
                    } catch (Exception e) {
                        failureCount++;
                        logger.error("Unexpected error fetching/publishing quote for {}: {}", symbol, e.getMessage(), e);
                    }
                }
            }

            logger.info("Market-data polling completed: {} successful, {} failed",
                successCount, failureCount);

        } catch (Exception e) {
            logger.error("Market-data polling failed with unexpected error", e);
        }
    }

    /**
     * Batch symbols into lists of at most maxBatchSize.
     *
     * @param symbols list of all symbols
     * @param maxBatchSize maximum symbols per batch (25)
     * @return list of batches
     */
    private List<List<String>> batchSymbols(List<String> symbols, int maxBatchSize) {
        List<List<String>> batches = new ArrayList<>();

        for (int i = 0; i < symbols.size(); i += maxBatchSize) {
            int end = Math.min(i + maxBatchSize, symbols.size());
            batches.add(symbols.subList(i, end));
        }

        return batches;
    }

    /**
     * Validate that the configured poll interval stays within the daily quota.
     * 
     * Quota: 2000 requests/day
     * Max symbols per request: 25
     * 
     * For N symbols:
     *   requestsPerDay = ceil(N / 25) * floor(86400 / interval)
     *   must be <= 2000
     * 
     * Enforces minimum interval of 44 seconds (for the declared 6 symbols).
     */
    private void validatePollInterval() {
        if (pollIntervalSeconds < MIN_POLL_INTERVAL_SECONDS) {
            throw new IllegalArgumentException(
                "market-data.poll-interval-seconds must be at least " + MIN_POLL_INTERVAL_SECONDS +
                " seconds to stay within Fauxnance quota (2000 requests/day). " +
                "Configured: " + pollIntervalSeconds + " seconds"
            );
        }
        logger.info("Poll interval validation passed: {} seconds (minimum: {} seconds)",
            pollIntervalSeconds, MIN_POLL_INTERVAL_SECONDS);
    }

    private BigDecimal midpoint(BigDecimal bid, BigDecimal ask) {
        return bid.add(ask).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal computeChangePct(BigDecimal previousLastPrice, BigDecimal currentLastPrice) {
        if (previousLastPrice == null || previousLastPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return currentLastPrice
            .subtract(previousLastPrice)
            .multiply(BigDecimal.valueOf(100))
            .divide(previousLastPrice, 4, RoundingMode.HALF_UP);
    }
}
