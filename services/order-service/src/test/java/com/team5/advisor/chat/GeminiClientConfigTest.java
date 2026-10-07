package com.team5.advisor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Runs the real Gemini SDK against a local server that accepts a request and never answers, as an overloaded
// Gemini does. The call must give up on its own instead of waiting forever.
class GeminiClientConfigTest {

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean stall = true;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (stall) {
                try {
                    release.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                byte[] body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}"
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    private String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void aCallGeminiNeverAnswersGivesUpAfterTheTimeout() {
        Client client = GeminiClientConfig.build("test-key", Duration.ofMillis(700), url());

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.models.generateContent("gemini-test", "hello", null))
            .isInstanceOf(RuntimeException.class);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        // one attempt, not the SDK's default of several retries each waiting out the timeout
        assertThat(elapsedMillis).isBetween(500L, 3_000L);
    }

    @Test
    void anAnsweredCallStillWorks() {
        stall = false;
        Client client = GeminiClientConfig.build("test-key", Duration.ofSeconds(5), url());

        assertThat(client.models.generateContent("gemini-test", "hello", null).text()).isEqualTo("OK");
    }
}
