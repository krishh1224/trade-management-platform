package com.team5.strategies.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.team5.auth.StrategyTokenProperties;
import com.team5.domain.enums.OrderSide;
import com.team5.dto.request.PlaceOrderRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TradeApiOrderClientTest {

    private static final String SECRET = "test-only-strategy-token-secret-at-least-32-bytes";

    private MockRestServiceServer server;
    private TradeApiOrderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trade-api");
        server = MockRestServiceServer.bindTo(builder).build();
        StrategyTokenIssuer issuer = new StrategyTokenIssuer(
            new StrategyTokenProperties(SECRET, Duration.ofSeconds(60)), Clock.systemUTC());
        client = new TradeApiOrderClient(builder.build(), issuer);
    }

    @Test
    void postsToTheTradeApisOrderRoute_withAShortLivedDelegatedToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.expect(requestTo("http://trade-api/api/v1/orders"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> authorization.set(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
            .andExpect(content().json("""
                {"accountId":101,"symbol":"INFY.NS","side":"BUY","quantity":10,"price":1515.51,
                 "idempotencyKey":"strat-7-key"}"""))
            .andRespond(withSuccess("""
                {"orderId":"ORD-1","status":"NEW","message":"Order accepted","symbol":"INFY.NS",
                 "side":"BUY","quantity":10,"price":1515.51}""", MediaType.APPLICATION_JSON));

        OrderPlacement placement = client.place(7L, request());

        server.verify();
        assertEquals(OrderPlacement.Result.ACCEPTED, placement.result());
        assertEquals("ORD-1", placement.orderId());

        assertTrue(authorization.get().startsWith("Bearer "));
        JWTClaimsSet claims = SignedJWT.parse(authorization.get().substring(7)).getJWTClaimsSet();
        assertEquals(StrategyTokenProperties.ISSUER, claims.getIssuer());
        assertEquals("strategy:7", claims.getSubject());
        assertEquals(101L, claims.getLongClaim("accountId"));
        assertEquals("orders:place", claims.getStringClaim("scope"));
        long lifetime = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;
        assertEquals(60, lifetime);
    }

    @Test
    void duplicateIdempotencyKey_isReportedAsDuplicate() {
        server.expect(requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"ORD-409\",\"message\":\"Duplicate order\"}"));

        OrderPlacement placement = client.place(7L, request());

        assertEquals(OrderPlacement.Result.DUPLICATE, placement.result());
    }

    @Test
    void refusedOrder_isRejected_withTheTradeApisCode() {
        server.expect(requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"ACC-403\",\"message\":\"Account not active\"}"));

        OrderPlacement placement = client.place(7L, request());

        assertEquals(OrderPlacement.Result.REJECTED, placement.result());
        assertEquals("ACC-403", placement.code());
        assertEquals("Trade API 403 ACC-403: Account not active", placement.detail());
    }

    @Test
    void serverError_isUncertain() {
        server.expect(requestTo("http://trade-api/api/v1/orders")).andRespond(withServerError());

        assertEquals(OrderPlacement.Result.UNCERTAIN, client.place(7L, request()).result());
    }

    @Test
    void withoutAStrategyKey_nothingIsSent() {
        StrategyTokenIssuer unconfigured = new StrategyTokenIssuer(
            new StrategyTokenProperties("", null), Clock.systemUTC());
        TradeApiOrderClient unconfiguredClient = new TradeApiOrderClient(RestClient.builder().build(), unconfigured);

        OrderPlacement placement = unconfiguredClient.place(7L, request());

        assertEquals(OrderPlacement.Result.REJECTED, placement.result());
        assertNull(placement.orderId());
        assertTrue(placement.detail().contains("STRATEGY_TOKEN_SECRET"));
    }

    private static PlaceOrderRequest request() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(101L);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("1515.51"));
        request.setIdempotencyKey("strat-7-key");
        return request;
    }
}
