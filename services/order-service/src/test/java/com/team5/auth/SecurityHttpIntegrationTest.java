package com.team5.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.OrderResponse;
import com.team5.service.AccountService;
import com.team5.service.OrderService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import com.team5.kafka.OrderEventPublisher;
import com.team5.notifications.api.AlertDelivery;
import com.team5.notifications.service.NotificationService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

// MockMvc never runs the servlet container's URI decoding, so path-based bypasses need a real server.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "auth.jwt.shared-secret=" + SecurityHttpIntegrationTest.SECRET
)
class SecurityHttpIntegrationTest {

    static final String SECRET = "integration-test-shared-secret-at-least-32-bytes";

    private static final String ORDER_ID = "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e";

    @LocalServerPort
    private int port;

    @MockBean
    private OrderService orderService;

    @MockBean
    private AccountService accountService;

    // the cancel route publishes ORDER_CANCELLED after commit; a test must never reach the real broker
    @MockBean
    private OrderEventPublisher orderEventPublisher;

    // the cancel route records a notification; a test must never write to the real database
    @MockBean
    private NotificationService notificationService;

    // the same bean also implements AlertDelivery for watchlist price alerts; mocking the service
    // above removes it, so the alert hand-off is mocked too
    @MockBean
    private AlertDelivery alertDelivery;

    private final HttpClient client = HttpClient.newHttpClient();

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/orders/" + ORDER_ID,
        "/%61pi/v1/orders/" + ORDER_ID,
        "/api;x=1/v1/orders/" + ORDER_ID,
        "/api/v1/orders;x=1/" + ORDER_ID,
        "/api/v1/./orders/" + ORDER_ID,
        "/api/v1/%2e/orders/" + ORDER_ID,
        "/api//v1/orders/" + ORDER_ID,
        "/API/v1/orders/" + ORDER_ID
    })
    void unauthenticatedCancelCannotBypassSecurityThroughPathTricks(String rawPath) throws Exception {
        HttpResponse<String> response = send("DELETE", rawPath, null);

        // 400 = rejected by Spring Security's StrictHttpFirewall; 401 = reached authorization without a token.
        assertTrue(response.statusCode() == 400 || response.statusCode() == 401,
            rawPath + " returned " + response.statusCode() + ": " + response.body());
        verifyNoInteractions(orderService);
    }

    @Test
    void validTokenReachesTheServiceWithTheTokensAccountId() throws Exception {
        UUID orderId = UUID.fromString(ORDER_ID);
        when(orderService.cancelOrder(101L, orderId)).thenReturn(new OrderResponse(
            "ORD-" + ORDER_ID, OrderStatus.CANCELLED, "Order cancelled", "INFY.NS", OrderSide.BUY, 1, BigDecimal.ONE));

        HttpResponse<String> response = send("DELETE", "/api/v1/orders/" + ORDER_ID, token(101L));

        assertEquals(200, response.statusCode(), response.body());
        verify(orderService).cancelOrder(101L, orderId);
    }

    @Test
    void unauthenticatedRequestToUnmappedPathIsDeniedByDefault() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v2/anything", null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("AUTH-401"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        HttpResponse<String> response = send("GET", "/health", null);

        assertNotEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"status\""));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/accounts/1", null);

        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }

    private static String token(long accountId) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"typ\":\"JWT\",\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        // iss must be "auth-service": trade-api rejects tokens from any other issuer.
        String payload = encoder.encodeToString(("{\"iss\":\"auth-service\",\"accountId\":" + accountId + ",\"exp\":"
            + Instant.now().plusSeconds(600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = encoder.encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return header + "." + payload + "." + signature;
    }

    private HttpResponse<String> send(String method, String rawPath, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
            .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
