package com.team5.strategies.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team5.kafka.KafkaEnvelope;
import com.team5.kafka.QuotePayload;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.engine.StrategyEngine;
import com.team5.strategies.engine.StrategyEngine.CycleResult;
import com.team5.strategies.exception.UnusableQuoteException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// The real-time price stream (S7): QUOTE events on market-data, the platform envelope around
// QuotePayload, keyed by symbol. Each quote runs one evaluation cycle, then its offset is committed.
// Per-symbol order is kept by the topic's keying, and this listener runs one record at a time.
@Component
public class StrategyMarketDataListener {

    private static final Logger log = LoggerFactory.getLogger(StrategyMarketDataListener.class);

    static final String TOPIC = "market-data";
    static final String QUOTE_EVENT = "QUOTE";
    private static final int MAX_SYMBOL_LENGTH = 20;

    private final StrategyEngine engine;
    private final ObjectMapper objectMapper;
    private final JavaType envelopeType;

    public StrategyMarketDataListener(StrategyEngine engine) {
        this.engine = engine;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.envelopeType = objectMapper.getTypeFactory()
            .constructParametricType(KafkaEnvelope.class, QuotePayload.class);
    }

    @KafkaListener(
        id = "strategies-market-data",
        topics = TOPIC,
        groupId = "${strategies.consumer.group-id:strategies-market-data}",
        containerFactory = StrategyConsumerConfig.CONTAINER_FACTORY,
        autoStartup = "${strategies.consumer.auto-startup:true}"
    )
    public void onQuote(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaEnvelope<QuotePayload> envelope = parse(record.value());
        if (!QUOTE_EVENT.equalsIgnoreCase(envelope.getEventType())) {
            log.debug("Ignoring {} eventId={}: not a quote", envelope.getEventType(), envelope.getEventId());
            ack.acknowledge();
            return;
        }
        if (envelope.getPayload() != null && envelope.getPayload().isStale()) {
            // past the provider's freshness window: neither part of the series nor a reason to trade
            log.debug("Ignoring stale quote eventId={}", envelope.getEventId());
            ack.acknowledge();
            return;
        }

        StrategyQuote quote = toQuote(envelope, record.key());
        CycleResult result = engine.onQuote(quote);
        if (result.fired() > 0) {
            log.info("Quote {} {} fired {} strategy rule(s), {} order(s) placed (partition={} offset={})",
                quote.symbol(), quote.price(), result.fired(), result.placed(), record.partition(), record.offset());
        } else {
            log.debug("Quote {} {} {}: {} strategies evaluated (partition={} offset={})", quote.symbol(), quote.price(),
                result.recorded(), result.evaluated(), record.partition(), record.offset());
        }
        ack.acknowledge();
    }

    KafkaEnvelope<QuotePayload> parse(String value) {
        if (value == null || value.isBlank()) {
            throw new UnusableQuoteException("EMPTY_MESSAGE", "market-data message has no value");
        }
        try {
            String json = value.trim();
            // the executor's producer may wrap its already-serialised JSON in a JSON string; unwrap once
            if (json.startsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            KafkaEnvelope<QuotePayload> envelope = objectMapper.readValue(json, envelopeType);
            if (envelope == null) {
                throw new UnusableQuoteException("MALFORMED_JSON", "market-data message is null JSON");
            }
            return envelope;
        } catch (JsonProcessingException e) {
            throw new UnusableQuoteException("MALFORMED_JSON", "market-data message is not valid JSON", e);
        }
    }

    StrategyQuote toQuote(KafkaEnvelope<QuotePayload> envelope, String key) {
        if (envelope.getEventId() == null) {
            // the event id is part of every order's idempotency key, so a quote without one cannot trade
            throw new UnusableQuoteException("MISSING_EVENT_ID", "quote has no eventId");
        }
        QuotePayload payload = envelope.getPayload();
        if (payload == null) {
            throw new UnusableQuoteException("MISSING_PAYLOAD", "quote " + envelope.getEventId() + " has no payload");
        }

        String symbol = payload.getSymbol() != null && !payload.getSymbol().isBlank() ? payload.getSymbol() : key;
        if (symbol == null || symbol.isBlank() || symbol.trim().length() > MAX_SYMBOL_LENGTH) {
            throw new UnusableQuoteException("MISSING_SYMBOL", "quote " + envelope.getEventId() + " has no usable symbol");
        }

        BigDecimal price = priceOf(payload);
        if (price == null) {
            throw new UnusableQuoteException("MISSING_PRICE", "quote " + envelope.getEventId() + " has no usable price");
        }

        OffsetDateTime quotedAt = envelope.getEventTime() != null ? envelope.getEventTime() : payload.getQuoteAsOf();
        if (quotedAt == null) {
            throw new UnusableQuoteException("MISSING_TIME", "quote " + envelope.getEventId() + " has no time");
        }

        // microseconds: what TIMESTAMPTZ keeps, so a redelivered quote compares equal to the stored one
        return new StrategyQuote(envelope.getEventId(), symbol.trim(), price,
            positiveOrNull(payload.getBid()), positiveOrNull(payload.getAsk()), quotedAt.truncatedTo(ChronoUnit.MICROS));
    }

    // The quote's price when it has one; otherwise the bid/ask midpoint to 4 places, half-up, the same
    // price the watchlists module and the executor's snapshot use.
    private BigDecimal priceOf(QuotePayload payload) {
        if (isPositive(payload.getPrice())) {
            return payload.getPrice();
        }
        if (isPositive(payload.getBid()) && isPositive(payload.getAsk())) {
            return payload.getBid().add(payload.getAsk()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return isPositive(value) ? value : null;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
