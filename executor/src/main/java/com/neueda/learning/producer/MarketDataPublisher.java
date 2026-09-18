package com.neueda.learning.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.event.KafkaEnvelope;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes QUOTE events to the market-data topic.
 * One Kafka message per quote, keyed by the symbol for ordering guarantees.
 */
@Component
public class MarketDataPublisher {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataPublisher.class);
    private static final String MARKET_DATA_TOPIC = "market-data";
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MarketDataPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Serialize OffsetDateTime as ISO-8601 strings instead of numeric timestamps
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Publish a QUOTE event for a symbol to the market-data topic.
     * Each quote is published as its own Kafka message, keyed by the symbol.
     *
     * @param symbol the instrument symbol (e.g., "INFY.NS")
     * @param quote the quote with bid/ask prices
     */
    public void publishQuote(String symbol, Quote quote) {
        // Build payload matching market-data contract from trade-api
        QuotePayload payload = new QuotePayload(symbol, quote.getBid(), quote.getAsk());

        KafkaEnvelope<?> envelope = new KafkaEnvelope<>(
            UUID.randomUUID(),
            "QUOTE",
            OffsetDateTime.now(),
            "executor",
            1,
            payload
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);

            logger.debug("Publishing QUOTE for {} to topic {} with key {}", 
                symbol, MARKET_DATA_TOPIC, symbol);

            SendResult<String, String> result = kafkaTemplate.send(MARKET_DATA_TOPIC, symbol, json)
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            logger.info("Published QUOTE for {} to partition {} offset {}",
                symbol,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize QUOTE for {}: {}", symbol, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while publishing QUOTE for {}: {}", symbol, e.getMessage());
        } catch (TimeoutException | ExecutionException e) {
            logger.error("Failed to publish QUOTE for {}: {}", symbol, e.getMessage());
        } catch (Exception e) {
            // Publish failures for market data should not stop the poller
            logger.error("Unexpected error publishing QUOTE for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Payload for QUOTE events on the market-data topic.
     * Simplified structure with symbol, bid, and ask prices.
     */
    public static class QuotePayload {
        public final String symbol;
        public final BigDecimal bid;
        public final BigDecimal ask;

        public QuotePayload(String symbol, BigDecimal bid, BigDecimal ask) {
            this.symbol = symbol;
            this.bid = bid;
            this.ask = ask;
        }

        @Override
        public String toString() {
            return "QuotePayload{" +
                    "symbol='" + symbol + '\'' +
                    ", bid=" + bid +
                    ", ask=" + ask +
                    '}';
        }
    }
}
