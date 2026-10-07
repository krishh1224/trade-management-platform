package com.team5.portfolio.pricing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.team5.portfolio.candles.Candle;
import com.team5.portfolio.candles.CandleSeries;
import com.team5.portfolio.config.FauxnanceProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Batch quote client. One call covers up to {@link #MAX_SYMBOLS_PER_CALL} symbols for a single unit
 * of quota. A symbol that comes back with an error entry is simply absent from the result; only a
 * failure of the call itself raises {@link QuoteFeedException}.
 */
@Component
public class FauxnanceClient {

    public static final int MAX_SYMBOLS_PER_CALL = 25;

    private static final Logger log = LoggerFactory.getLogger(FauxnanceClient.class);

    private final RestClient restClient;
    private final FauxnanceProperties properties;

    public FauxnanceClient(RestClient fauxnanceRestClient, FauxnanceProperties properties) {
        this.restClient = fauxnanceRestClient;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    public Map<String, PricedQuote> fetch(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        if (symbols.size() > MAX_SYMBOLS_PER_CALL) {
            throw new IllegalArgumentException("At most " + MAX_SYMBOLS_PER_CALL + " symbols per call");
        }
        if (!isConfigured()) {
            throw new QuoteFeedException("FAUXNANCE_API_KEY is not configured", null);
        }

        BatchResponse response;
        try {
            response = restClient.get()
                .uri(uri -> uri.path("/quotes").queryParam("symbols", String.join(",", symbols)).build())
                .retrieve()
                .body(BatchResponse.class);
        } catch (RuntimeException e) {
            throw new QuoteFeedException("Fauxnance batch quote call failed: " + e.getMessage(), e);
        }
        if (response == null || response.data() == null || response.data().quotes() == null) {
            throw new QuoteFeedException("Fauxnance batch quote response was empty", null);
        }

        Map<String, PricedQuote> quotes = new LinkedHashMap<>();
        for (Entry entry : response.data().quotes()) {
            PricedQuote quote = toQuote(entry);
            if (quote == null) {
                log.warn("No usable quote for {}: {}", entry.symbol(), entry.error());
            } else {
                quotes.put(entry.symbol(), quote);
            }
        }
        return quotes;
    }

    /**
     * Daily candles for one symbol, oldest first. Fauxnance ignores any date window and returns about
     * a year per call, one unit of quota each, so callers cache the result. A malformed bar is dropped
     * rather than failing the series.
     */
    public CandleSeries candles(String symbol) {
        if (!isConfigured()) {
            throw new QuoteFeedException("FAUXNANCE_API_KEY is not configured", null);
        }
        JsonNode body;
        try {
            body = restClient.get().uri("/candles/{symbol}", symbol).retrieve().body(JsonNode.class);
        } catch (RuntimeException e) {
            throw new QuoteFeedException("Fauxnance candle call failed: " + e.getMessage(), e);
        }
        JsonNode data = body == null ? null : body.get("data");
        if (data == null || !data.path("candles").isArray()) {
            throw new QuoteFeedException("Fauxnance candle response was empty", null);
        }

        List<Candle> candles = new ArrayList<>();
        for (JsonNode node : data.get("candles")) {
            Candle candle = toCandle(node);
            if (candle == null) {
                log.warn("Dropping malformed candle for {}: {}", symbol, node);
            } else {
                candles.add(candle);
            }
        }
        candles.sort((a, b) -> a.date().compareTo(b.date()));
        return new CandleSeries(symbol, data.path("currency").asText(""), List.copyOf(candles));
    }

    /** Remaining daily quota, or -1 when the usage call fails. Costs quota, so callers must cache. */
    public int quotaRemaining() {
        try {
            UsageResponse usage = restClient.get().uri("/usage").retrieve().body(UsageResponse.class);
            if (usage == null || usage.data() == null) {
                return -1;
            }
            return Math.max(usage.data().dailyQuota() - usage.data().usedToday(), 0);
        } catch (RuntimeException e) {
            log.warn("Fauxnance usage call failed: {}", e.getMessage());
            return -1;
        }
    }

    private static PricedQuote toQuote(Entry entry) {
        Quote quote = entry.quote();
        if (entry.symbol() == null || quote == null || quote.price() == null
            || quote.price().signum() <= 0 || quote.asOf() == null) {
            return null;
        }
        return new PricedQuote(entry.symbol(), quote.price(), quote.currency(), quote.asOf(),
            Boolean.TRUE.equals(entry.stale()));
    }

    private static Candle toCandle(JsonNode node) {
        try {
            LocalDate date = LocalDate.parse(node.path("date").asText(""));
            BigDecimal open = price(node, "open");
            BigDecimal high = price(node, "high");
            BigDecimal low = price(node, "low");
            BigDecimal close = price(node, "close");
            JsonNode volume = node.get("volume");
            if (open == null || high == null || low == null || close == null || high.compareTo(low) < 0
                || volume == null || !volume.canConvertToLong() || volume.asLong() < 0) {
                return null;
            }
            return new Candle(date, open, high, low, close, volume.asLong(), node.path("synthetic").asBoolean(false));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Four decimal places keep sub-unit instruments such as FX pairs intact and drop float noise.
    private static BigDecimal price(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || value.decimalValue().signum() <= 0) {
            return null;
        }
        return value.decimalValue().setScale(4, RoundingMode.HALF_UP);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchResponse(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(List<Entry> quotes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String symbol, Boolean stale, Quote quote, Map<String, Object> error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quote(BigDecimal price, String currency, Instant asOf) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageResponse(Usage data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(int dailyQuota, int usedToday) {
    }
}
