package com.team5.watchlists.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team5.kafka.KafkaEnvelope;
import com.team5.kafka.QuotePayload;
import com.team5.watchlists.domain.MarketQuote;
import com.team5.watchlists.domain.QuoteEvaluation;
import com.team5.watchlists.exception.InvalidQuoteException;
import com.team5.watchlists.service.PriceAlertEvaluator;
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

// Reads QUOTE events from market-data (the platform envelope around QuotePayload, keyed by symbol),
// evaluates the symbol's price alerts, then commits the offset. Per-symbol order is kept by the
// topic's keying, and this listener runs one record at a time per partition.
@Component
public class MarketDataQuoteListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataQuoteListener.class);

    static final String TOPIC = "market-data";
    static final String QUOTE_EVENT = "QUOTE";
    private static final int MAX_SYMBOL_LENGTH = 20;

    private final PriceAlertEvaluator evaluator;
    private final ObjectMapper objectMapper;
    private final JavaType envelopeType;

    public MarketDataQuoteListener(PriceAlertEvaluator evaluator) {
        this.evaluator = evaluator;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.envelopeType = objectMapper.getTypeFactory()
            .constructParametricType(KafkaEnvelope.class, QuotePayload.class);
    }

    @KafkaListener(
        id = "watchlists-market-data",
        topics = TOPIC,
        groupId = "${watchlists.consumer.group-id:watchlists-market-data}",
        containerFactory = WatchlistConsumerConfig.CONTAINER_FACTORY,
        autoStartup = "${watchlists.consumer.auto-startup:true}"
    )
    public void onQuote(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaEnvelope<QuotePayload> envelope = parse(record.value());
        if (!QUOTE_EVENT.equalsIgnoreCase(envelope.getEventType())) {
            log.debug("Ignoring {} eventId={}: not a quote", envelope.getEventType(), envelope.getEventId());
            ack.acknowledge();
            return;
        }

        MarketQuote quote = toQuote(envelope, record.key());
        // the quote's transaction (price, alert states, notifications) has committed when this returns
        QuoteEvaluation result = evaluator.evaluate(quote);
        if (result.triggered() > 0) {
            log.info("Quote {} {} triggered {} price alert(s) (partition={} offset={})",
                quote.symbol(), quote.price(), result.triggered(), record.partition(), record.offset());
        } else {
            log.debug("Quote {} {} {} (partition={} offset={})",
                quote.symbol(), quote.price(), result.outcome(), record.partition(), record.offset());
        }
        ack.acknowledge();
    }

    KafkaEnvelope<QuotePayload> parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidQuoteException("EMPTY_MESSAGE", "market-data message has no value");
        }
        try {
            String json = value.trim();
            // the executor's producer may wrap its already-serialised JSON in a JSON string; unwrap
            // once, as the notifications consumer does for trade-events
            if (json.startsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            KafkaEnvelope<QuotePayload> envelope = objectMapper.readValue(json, envelopeType);
            if (envelope == null) {
                throw new InvalidQuoteException("MALFORMED_JSON", "market-data message is null JSON");
            }
            return envelope;
        } catch (JsonProcessingException e) {
            throw new InvalidQuoteException("MALFORMED_JSON", "market-data message is not valid JSON", e);
        }
    }

    MarketQuote toQuote(KafkaEnvelope<QuotePayload> envelope, String key) {
        QuotePayload payload = envelope.getPayload();
        if (payload == null) {
            throw new InvalidQuoteException("MISSING_PAYLOAD", "quote " + envelope.getEventId() + " has no payload");
        }

        String symbol = payload.getSymbol() != null && !payload.getSymbol().isBlank() ? payload.getSymbol() : key;
        if (symbol == null || symbol.isBlank() || symbol.trim().length() > MAX_SYMBOL_LENGTH) {
            throw new InvalidQuoteException("MISSING_SYMBOL", "quote " + envelope.getEventId() + " has no usable symbol");
        }

        BigDecimal price = priceOf(payload);
        if (price == null) {
            throw new InvalidQuoteException("MISSING_PRICE", "quote " + envelope.getEventId() + " has no usable price");
        }

        // the producer's eventTime orders quotes; quoteAsOf (when the provider observed it) if absent
        OffsetDateTime quotedAt = envelope.getEventTime() != null ? envelope.getEventTime() : payload.getQuoteAsOf();
        if (quotedAt == null) {
            throw new InvalidQuoteException("MISSING_TIME", "quote " + envelope.getEventId() + " has no time");
        }

        // microseconds: what TIMESTAMPTZ keeps, so a redelivered quote compares equal to the stored one
        return new MarketQuote(envelope.getEventId(), symbol.trim(), price, quotedAt.truncatedTo(ChronoUnit.MICROS));
    }

    // The quote's price when it has one; otherwise the bid/ask midpoint to 4 places, half-up, which is
    // what the executor stores as the last price in its own snapshot.
    private BigDecimal priceOf(QuotePayload payload) {
        if (isPositive(payload.getPrice())) {
            return payload.getPrice();
        }
        if (isPositive(payload.getBid()) && isPositive(payload.getAsk())) {
            return payload.getBid().add(payload.getAsk()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        }
        return null;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
