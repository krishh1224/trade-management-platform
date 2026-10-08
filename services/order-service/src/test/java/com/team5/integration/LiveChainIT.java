package com.team5.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class LiveChainIT {

    private static final long ACCOUNT_ID = Long.parseLong(System.getProperty("live.chain.account-id", "102"));
    private static final String SYMBOL = "INFY.NS";
    private static final String AUTH_USERNAME = System.getProperty("live.chain.auth.username", "");
    private static final String AUTH_PASSWORD = System.getProperty("live.chain.auth.password", "");

    private static final String AUTH_BASE = System.getProperty("live.chain.auth-base", "http://localhost:3000");
    private static final String TRADE_BASE = System.getProperty("live.chain.trade-base", "http://localhost:8081");
    private static final String EXECUTOR_BASE = System.getProperty("live.chain.executor-base", "http://localhost:8082");
    private static final String KAFKA_BOOTSTRAP =
        System.getProperty("live.chain.kafka-bootstrap", envOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:8085"));

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @Test
    void liveDataChain_preferencesToTradeAndWatchlistAlerts_withPortfolioReachability() throws Exception {
        Instant testStarted = Instant.now();

        waitForHealth("auth service", AUTH_BASE + "/health");
        waitForHealth("trade API", TRADE_BASE + "/health");
        waitForHttpReachable("executor service", EXECUTOR_BASE + "/");

        String token = signInAgainstTheRealAuthService();
        assertRouteAuthorisationAndReachability(token);

        JsonNode preferences = putJson(
            TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/preferences",
            token,
            """
                {"currency":"INR","theme":"DARK","alertChannel":"BROWSER"}
                """
        ).json();
        assertEquals("BROWSER", preferences.path("alertChannel").asText(), preferences.toString());

        JsonNode notificationsBeforeTrade = listNotifications(token);
        Set<Long> knownNotificationIds = notificationIds(notificationsBeforeTrade);

        JsonNode orderResponse = postJson(
            TRADE_BASE + "/api/v1/orders",
            token,
            """
                {
                  "accountId": %d,
                  "symbol": "AAPL",
                  "side": "BUY",
                  "quantity": 1,
                  "price": 100.00,
                  "idempotencyKey": "%s"
                }
                """.formatted(ACCOUNT_ID, "live-chain-" + UUID.randomUUID())
        ).json();
        assertEquals("NEW", orderResponse.path("status").asText(), orderResponse.toString());

        await("trade outcome notification on preferred channel", Duration.ofSeconds(90), () -> {
            try {
                JsonNode rows = listNotifications(token);
                for (JsonNode row : rows) {
                    long id = row.path("notificationId").asLong(-1);
                    if (id <= 0 || knownNotificationIds.contains(id)) {
                        continue;
                    }
                    String type = row.path("type").asText("");
                    if (("ORDER_FILLED".equals(type) || "ORDER_REJECTED".equals(type) || "ORDER_CANCELLED".equals(type))
                        && "BROWSER".equals(row.path("channel").asText(""))) {
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });

        JsonNode watchlistGroup = postJson(
            TRADE_BASE + "/api/v1/watchlists/groups",
            token,
            """
                {"name":"LiveChain %s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8))
        ).json();
        long watchlistGroupId = watchlistGroup.path("watchlistGroupId").asLong();
        assertTrue(watchlistGroupId > 0, watchlistGroup.toString());

        JsonNode watchlistItem = postJson(
            TRADE_BASE + "/api/v1/watchlists",
            token,
            """
                {
                  "accountId": %d,
                  "symbol": "%s",
                  "watchlistGroupId": %d
                }
                """.formatted(ACCOUNT_ID, SYMBOL, watchlistGroupId)
        ).json();
        long watchlistId = watchlistItem.path("watchlistId").asLong();
        assertTrue(watchlistId > 0, watchlistItem.toString());

        BigDecimal threshold = new BigDecimal("1500.2500");
        OffsetDateTime baseTime = OffsetDateTime.now().plusDays(1);
        publishMarketQuote(SYMBOL, threshold.subtract(new BigDecimal("10.0000")), baseTime);

        await("baseline quote visible on watchlist row", Duration.ofSeconds(30), () -> {
            try {
                JsonNode watchlist = getJson(TRADE_BASE + "/api/v1/watchlists", token).json();
                JsonNode row = findWatchlistRow(watchlist, watchlistId);
                if (row == null || row.path("lastPrice").isMissingNode() || row.path("lastPrice").isNull()) {
                    return false;
                }
                BigDecimal lastPrice = new BigDecimal(row.path("lastPrice").asText());
                return lastPrice.compareTo(threshold) < 0;
            } catch (Exception e) {
                return false;
            }
        });

        JsonNode alert = postJson(
            TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/price-alerts",
            token,
            """
                {
                  "watchlistId": %d,
                  "direction": "ABOVE",
                  "threshold": %s
                }
                """.formatted(watchlistId, threshold.toPlainString())
        ).json();
        long alertId = alert.path("alertId").asLong();
        assertTrue(alertId > 0, alert.toString());

        publishMarketQuote(SYMBOL, threshold.add(new BigDecimal("10.0000")), baseTime.plusSeconds(5));

        await("price alert row reaches TRIGGERED", Duration.ofSeconds(60), () -> {
            try {
                JsonNode alerts = getJson(TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/price-alerts", token).json();
                if (!alerts.path("alerts").isArray()) {
                    return false;
                }
                for (JsonNode row : alerts.path("alerts")) {
                    if (row.path("alertId").asLong(-1) == alertId
                        && "TRIGGERED".equals(row.path("status").asText(""))) {
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });

        await("watchlist alert delivered on preferred channel", Duration.ofSeconds(60), () -> {
            try {
                JsonNode rows = listNotifications(token);
                for (JsonNode row : rows) {
                    if (row.path("createdAt").asText("").isEmpty()) {
                        continue;
                    }
                    String type = row.path("type").asText("");
                    String channel = row.path("channel").asText("");
                    String message = row.path("message").asText("");
                    if (!"PRICE_ALERT".equals(type) || !"BROWSER".equals(channel)) {
                        continue;
                    }
                    Instant createdAt = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(row.path("createdAt").asText()));
                    if (createdAt.isBefore(testStarted)) {
                        continue;
                    }
                    if (message.contains(SYMBOL)) {
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void assertRouteAuthorisationAndReachability(String token) throws Exception {
        assertEquals(401, getRaw(TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/preferences", null).statusCode());
        assertEquals(200, getRaw(TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/preferences", token).statusCode());

        assertEquals(401, getRaw(TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/notifications", null).statusCode());
        assertEquals(200, getRaw(TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/notifications", token).statusCode());

        assertEquals(401, getRaw(TRADE_BASE + "/api/v1/watchlists", null).statusCode());
        assertEquals(200, getRaw(TRADE_BASE + "/api/v1/watchlists", token).statusCode());

        assertEquals(401, getRaw(TRADE_BASE + "/api/v1/portfolio/" + ACCOUNT_ID, null).statusCode());
        int portfolioStatus = getRaw(TRADE_BASE + "/api/v1/portfolio/" + ACCOUNT_ID, token).statusCode();
        assertTrue(portfolioStatus == 200 || portfolioStatus == 503,
            "expected portfolio route to be reachable with auth, got " + portfolioStatus);
    }

    private String signInAgainstTheRealAuthService() throws Exception {
        String username = AUTH_USERNAME.isBlank()
            ? "live.chain." + UUID.randomUUID().toString().substring(0, 8)
            : AUTH_USERNAME;
        String password = AUTH_PASSWORD.isBlank() ? "LiveChain!Pass123" : AUTH_PASSWORD;

        if (AUTH_USERNAME.isBlank()) {
            HttpResult register = postJson(
                AUTH_BASE + "/auth/register",
                null,
                """
                    {
                      "username": "%s",
                      "password": "%s",
                      "accountId": %d
                    }
                    """.formatted(username, password, ACCOUNT_ID)
            );
            assertEquals(201, register.statusCode(), "register: " + register.body());
        }

        HttpResult login = postJson(
            AUTH_BASE + "/auth/login",
            null,
            """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password)
        );
        assertTrue(login.statusCode() == 200 || login.statusCode() == 201,
            "login status=" + login.statusCode() + ", body=" + login.body());

        String token = login.json().path("accessToken").asText(null);
        assertNotNull(token, "login response has no accessToken: " + login.body());
        return token;
    }

    private JsonNode listNotifications(String token) throws Exception {
        return getJson(TRADE_BASE + "/api/v1/accounts/" + ACCOUNT_ID + "/notifications?limit=100", token).json();
    }

    private static Set<Long> notificationIds(JsonNode notifications) {
        Set<Long> ids = new HashSet<>();
        if (notifications == null || !notifications.isArray()) {
            return ids;
        }
        for (JsonNode notification : notifications) {
            long id = notification.path("notificationId").asLong(-1);
            if (id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    private JsonNode findWatchlistRow(JsonNode watchlistRows, long watchlistId) {
        if (watchlistRows == null || !watchlistRows.isArray()) {
            return null;
        }
        for (JsonNode row : watchlistRows) {
            if (row.path("watchlistId").asLong(-1) == watchlistId) {
                return row;
            }
        }
        return null;
    }

    private void publishMarketQuote(String symbol, BigDecimal price, OffsetDateTime time) throws Exception {
        String payload = """
            {
              "eventId": "%s",
              "eventType": "QUOTE",
              "eventTime": "%s",
              "source": "live-chain-it",
              "schemaVersion": 1,
              "payload": {
                "symbol": "%s",
                "bid": %s,
                "ask": %s
              }
            }
            """.formatted(
            UUID.randomUUID(),
            time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            symbol,
            price.toPlainString(),
            price.toPlainString()
        );

        java.util.Properties props = new java.util.Properties();
        props.put("bootstrap.servers", KAFKA_BOOTSTRAP);
        props.put("acks", "all");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            RecordMetadata metadata = producer.send(new ProducerRecord<>("market-data", symbol, payload)).get();
            assertNotNull(metadata, "quote publish returned no metadata");
        }
    }

    private void waitForHealth(String serviceName, String healthUrl) {
        await(serviceName + " health at " + healthUrl, Duration.ofSeconds(60), () -> {
            try {
                HttpResponse<String> response = getRaw(healthUrl, null);
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void waitForHttpReachable(String serviceName, String url) {
        await(serviceName + " reachability at " + url, Duration.ofSeconds(60), () -> {
            try {
                HttpResponse<String> response = getRaw(url, null);
                return response.statusCode() >= 100 && response.statusCode() < 600;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static void await(String what, Duration timeout, Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(timeout);
        Throwable lastError = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                if (condition.get()) {
                    return;
                }
            } catch (Throwable t) {
                lastError = t;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for " + what);
            }
        }
        if (lastError != null) {
            fail("Timed out waiting for " + what + ": " + lastError.getMessage());
        }
        fail("Timed out waiting for " + what);
    }

    private HttpResult getJson(String url, String token) throws Exception {
        HttpResponse<String> response = getRaw(url, token);
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
            "GET " + url + " status=" + response.statusCode() + " body=" + response.body());
        return HttpResult.of(response);
    }

    private HttpResult postJson(String url, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
            "POST " + url + " status=" + response.statusCode() + " body=" + response.body());
        return HttpResult.of(response);
    }

    private HttpResult putJson(String url, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
            "PUT " + url + " status=" + response.statusCode() + " body=" + response.body());
        return HttpResult.of(response);
    }

    private HttpResponse<String> getRaw(String url, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record HttpResult(int statusCode, String body, JsonNode json) {
        static HttpResult of(HttpResponse<String> response) throws IOException {
            JsonNode parsed;
            String body = response.body() == null ? "" : response.body();
            if (body.isBlank()) {
                parsed = JSON.nullNode();
            } else {
                parsed = JSON.readTree(body);
            }
            return new HttpResult(response.statusCode(), body, parsed);
        }
    }
}