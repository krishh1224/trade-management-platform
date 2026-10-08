package com.team5.portfolio.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team5.portfolio.candles.Candle;
import com.team5.portfolio.candles.CandleSeries;
import com.team5.portfolio.config.FauxnanceProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Response bodies are captured from the real Fauxnance API, so no quota is spent.
class FauxnanceClientTest {

    private static final String BATCH = """
        {"data":{"quotes":[
          {"symbol":"AAPL","source":"cache","stale":true,"quote":{"symbol":"AAPL","price":333.59,"bid":333.55,
            "ask":333.63,"currency":"USD","change":0.7,"changePercent":0.21,"previousClose":332.89,
            "asOf":"2026-10-06T14:47:09Z","marketState":"unknown"}},
          {"symbol":"NOTAREAL123","error":{"code":"UPSTREAM_UNAVAILABLE",
            "message":"Symbol discovery is temporarily unavailable; retry later.","details":{}}}
        ]},"meta":{"asOf":"2026-10-06T14:53:12Z"}}""";

    private static final String CANDLES = """
        {"data":{"symbol":"INFY.NS","interval":"1d","currency":"INR","candles":[
          {"date":"2025-08-05","open":1620.0,"high":1635.5,"low":1612.0,"close":1630.25,"volume":9102300,"synthetic":true},
          {"date":"2025-08-04","open":1599.9999755859375,"high":1622.4,"low":1595.1,"close":1618.9,"volume":7891200,"synthetic":false},
          {"date":"2025-08-01","open":100,"high":85,"low":95,"close":90,"volume":10},
          {"date":"2025-08-06","open":100,"high":110,"low":95,"close":-5.5,"volume":10},
          {"date":"2025-08-07","open":100,"high":110,"low":95,"close":105,"volume":-500},
          {"date":null,"open":100,"high":110,"low":95,"close":105,"volume":10},
          {"date":"2025-08-08","high":110,"low":95,"close":105,"volume":10},
          {"date":"2025-08-11","open":"not_a_number","high":110,"low":95,"close":105,"volume":10}
        ]}}""";

    private MockRestServiceServer server;
    private FauxnanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://fauxnance").defaultHeader("x-api-key", "key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FauxnanceClient(builder.build(), properties("key"));
    }

    @Test
    void oneBatchedGetCarriesEverySymbolAndTheApiKey() {
        server.expect(requestTo("http://fauxnance/quotes?symbols=AAPL,NOTAREAL123,FX:USDINR"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-api-key", "key"))
            .andRespond(withSuccess(BATCH, MediaType.APPLICATION_JSON));

        client.fetch(List.of("AAPL", "NOTAREAL123", "FX:USDINR"));

        server.verify();
    }

    @Test
    void aQuoteEntryIsParsedAndAnErrorEntryIsLeftOut() {
        server.expect(requestTo("http://fauxnance/quotes?symbols=AAPL,NOTAREAL123"))
            .andRespond(withSuccess(BATCH, MediaType.APPLICATION_JSON));

        Map<String, PricedQuote> quotes = client.fetch(List.of("AAPL", "NOTAREAL123"));

        assertEquals(1, quotes.size());
        PricedQuote aapl = quotes.get("AAPL");
        assertEquals(new BigDecimal("333.59"), aapl.price());
        assertEquals("USD", aapl.currency());
        assertEquals(Instant.parse("2026-10-06T14:47:09Z"), aapl.asOf());
        assertTrue(aapl.stale());
        assertFalse(quotes.containsKey("NOTAREAL123"));
    }

    @Test
    void aFailedCallRaisesAFeedException() {
        server.expect(requestTo("http://fauxnance/quotes?symbols=AAPL")).andRespond(withServerError());

        assertThrows(QuoteFeedException.class, () -> client.fetch(List.of("AAPL")));
    }

    @Test
    void moreThanTwentyFiveSymbolsInOneCallIsRefused() {
        List<String> tooMany = java.util.stream.IntStream.range(0, 26).mapToObj(i -> "S" + i).toList();

        assertThrows(IllegalArgumentException.class, () -> client.fetch(tooMany));
    }

    @Test
    void withoutAnApiKeyNoCallIsMade() {
        FauxnanceClient unconfigured = new FauxnanceClient(RestClient.create("http://fauxnance"), properties(""));

        assertFalse(unconfigured.isConfigured());
        assertThrows(QuoteFeedException.class, () -> unconfigured.fetch(List.of("AAPL")));
    }

    @Test
    void quotaRemainingIsTheDailyQuotaLessWhatWasUsed() {
        server.expect(requestTo("http://fauxnance/usage")).andRespond(withSuccess(
            "{\"data\":{\"keyLabel\":\"x\",\"dailyQuota\":2000,\"usedToday\":193,\"resetsAt\":\"2026-10-07T00:00:00Z\"}}",
            MediaType.APPLICATION_JSON));

        assertEquals(1807, client.quotaRemaining());
    }

    @Test
    void quotaRemainingIsMinusOneWhenTheUsageCallFails() {
        server.expect(requestTo("http://fauxnance/usage")).andRespond(withServerError());

        assertEquals(-1, client.quotaRemaining());
    }

    @Test
    void candlesAreParsedSortedAndRoundedAndMalformedBarsAreDropped() {
        server.expect(requestTo("http://fauxnance/candles/INFY.NS")).andExpect(method(HttpMethod.GET))
            .andExpect(header("x-api-key", "key"))
            .andRespond(withSuccess(CANDLES, MediaType.APPLICATION_JSON));

        CandleSeries series = client.candles("INFY.NS");

        assertEquals("INR", series.currency());
        assertEquals(2, series.candles().size());
        Candle first = series.candles().get(0);
        assertEquals(LocalDate.parse("2025-08-04"), first.date());
        assertEquals(new BigDecimal("1600.0000"), first.open());
        assertFalse(first.synthetic());
        assertEquals(LocalDate.parse("2025-08-05"), series.candles().get(1).date());
        assertTrue(series.candles().get(1).synthetic());
    }

    @Test
    void aSyntheticBarWithNullVolume_isKeptWithZeroVolume() {
        server.expect(requestTo("http://fauxnance/candles/AAPL"))
            .andRespond(withSuccess("""
                {"data":{"symbol":"AAPL","interval":"1d","currency":"USD","candles":[
                  {"date":"2026-10-08","open":337.48156499,"high":347.66899097,"low":335.71214483,
                   "close":345.8556653,"adjclose":345.8556653,"volume":null,"synthetic":true},
                  {"date":"2026-10-07","open":330,"high":338,"low":329,"close":337,"synthetic":true},
                  {"date":"2026-10-06","open":330,"high":338,"low":329,"close":337,"volume":"lots"}
                ]}}""", MediaType.APPLICATION_JSON));

        CandleSeries series = client.candles("AAPL");

        // null or absent volume is kept as 0; a volume that is there but not a number is still malformed
        assertEquals(2, series.candles().size());
        Candle latest = series.candles().get(1);
        assertEquals(LocalDate.parse("2026-10-08"), latest.date());
        assertEquals(0L, latest.volume());
        assertTrue(latest.synthetic());
    }

    @Test
    void aFailedCandleCallRaisesAFeedException() {
        server.expect(requestTo("http://fauxnance/candles/INFY.NS")).andRespond(withServerError());

        assertThrows(QuoteFeedException.class, () -> client.candles("INFY.NS"));
    }

    @Test
    void aCandleResponseWithoutABarsArrayRaisesAFeedException() {
        server.expect(requestTo("http://fauxnance/candles/INFY.NS"))
            .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        assertThrows(QuoteFeedException.class, () -> client.candles("INFY.NS"));
    }

    private static FauxnanceProperties properties(String apiKey) {
        return new FauxnanceProperties("http://fauxnance", apiKey, Duration.ofSeconds(60), Duration.ofSeconds(2),
            Duration.ofSeconds(4), Duration.ofMinutes(5), Duration.ofHours(1));
    }
}
