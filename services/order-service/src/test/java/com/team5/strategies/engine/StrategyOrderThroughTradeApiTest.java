package com.team5.strategies.engine;

import static com.team5.strategies.StrategyFixtures.OWNER;
import static com.team5.strategies.StrategyFixtures.SYMBOL;
import static com.team5.strategies.StrategyFixtures.defaultProperties;
import static com.team5.strategies.StrategyFixtures.maCrossover;
import static com.team5.strategies.StrategyFixtures.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.JwtProperties;
import com.team5.auth.SecurityConfig;
import com.team5.auth.StrategyTokenProperties;
import com.team5.controller.OrderController;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.kafka.OrderEventPublisher;
import com.team5.notifications.service.NotificationService;
import com.team5.service.OrderService;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.PositionSnapshot;
import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyReadMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/**
 * A fired strategy's order travels the Trade API's own route: StrategyExecutor → TradeApiOrderClient
 * → HTTP → SecurityConfig (strategy token verified) → OrderController → OrderService, with the
 * strategy's account as the authenticated account. The HTTP hop runs through MockMvc, so the filter
 * chain and the controller are the real ones; only the order service behind the route is mocked.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class})
class StrategyOrderThroughTradeApiTest {

    private static final SignalResult BUY = SignalResult.buy("Golden cross: test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StrategyTokenProperties strategyTokenProperties;

    @Autowired
    private JwtProperties jwtProperties;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @MockBean
    private NotificationService notificationService;

    @Test
    void triggeredStrategy_placesItsOrderThroughPostApiV1Orders() {
        StrategyMapper strategyMapper = mock(StrategyMapper.class);
        StrategyExecutionMapper executionMapper = mock(StrategyExecutionMapper.class);
        StrategyReadMapper readMapper = mock(StrategyReadMapper.class);
        when(strategyMapper.lockById(7L)).thenReturn(maCrossover(7L));
        when(readMapper.findPosition(OWNER, SYMBOL)).thenReturn(new PositionSnapshot(0, 0, 0));
        when(orderService.placeOrder(eq(OWNER), any())).thenReturn(new OrderResponse(
            "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e", OrderStatus.NEW, "Order accepted", SYMBOL,
            OrderSide.BUY, 10, new BigDecimal("1515.51")));

        StrategyExecutor executor = new StrategyExecutor(strategyMapper, executionMapper, readMapper,
            orderClientThroughTheFilterChain(), defaultProperties(), Clock.systemUTC());
        StrategyQuote quote = quote("1500.00");

        ExecutionRow execution = executor.execute(7L, BUY, quote);

        // the route authenticated the strategy's token as its owner's account
        ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(orderService).placeOrder(eq(OWNER), request.capture());
        assertEquals(OWNER, request.getValue().getAccountId());
        assertEquals("strat-7-" + quote.eventId(), request.getValue().getIdempotencyKey());
        assertEquals(new BigDecimal("1515.51"), request.getValue().getPrice());
        // and published ORDER_PLACED after commit, exactly as for a customer's own order
        verify(orderEventPublisher).publishOrderPlaced(any(), any(), any());

        assertEquals(ExecutionOutcome.PLACED, execution.getOutcome());
        assertEquals("ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e", execution.getOrderId());
    }

    @Test
    void routeRefusingTheOrder_isRecordedAsAFailure() {
        StrategyMapper strategyMapper = mock(StrategyMapper.class);
        StrategyReadMapper readMapper = mock(StrategyReadMapper.class);
        when(strategyMapper.lockById(7L)).thenReturn(maCrossover(7L));
        when(readMapper.findPosition(OWNER, SYMBOL)).thenReturn(new PositionSnapshot(0, 0, 0));
        when(orderService.placeOrder(anyLong(), any())).thenThrow(new com.team5.domain.exception.AccountNotActiveException());

        StrategyExecutor executor = new StrategyExecutor(strategyMapper, mock(StrategyExecutionMapper.class), readMapper,
            orderClientThroughTheFilterChain(), defaultProperties(), Clock.systemUTC());

        ExecutionRow execution = executor.execute(7L, BUY, quote("1500.00"));

        assertEquals(ExecutionOutcome.FAILED, execution.getOutcome());
        assertEquals("Trade API 403 ACC-403: Account not active", execution.getDetail());
        verify(strategyMapper).recordFailure(eq(7L), eq(BigDecimal.ZERO), any(), any());
    }

    @Test
    void strategyToken_isRefusedOnEveryRouteButPlacingAnOrder() throws Exception {
        String token = new StrategyTokenIssuer(strategyTokenProperties, Clock.systemUTC()).issue(7L, OWNER);

        mockMvc.perform(get("/api/v1/accounts/" + OWNER).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));
        mockMvc.perform(get("/api/v1/accounts/" + OWNER + "/strategies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/accounts/" + OWNER + "/strategies/7/enable").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/orders/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void strategyClaims_signedWithTheCustomerKey_areRejected() throws Exception {
        String forged = token(jwtProperties.sharedSecret(), claims -> {});

        placeOrderWith(forged).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void customerIssuer_signedWithTheStrategyKey_isRejected() throws Exception {
        String forged = token(strategyTokenProperties.sharedSecret(), claims -> claims.issuer("auth-service"));

        placeOrderWith(forged).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void strategyTokenWithoutTheOrdersScope_isRejected() throws Exception {
        String token = token(strategyTokenProperties.sharedSecret(), claims -> claims.claim("scope", "accounts:read"));

        placeOrderWith(token).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void strategyTokenLivingLongerThanItsTtl_isRejected() throws Exception {
        String token = token(strategyTokenProperties.sharedSecret(),
            claims -> claims.expiresAt(Instant.now().plusSeconds(3600)));

        placeOrderWith(token).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void strategyTokenForAnotherAccount_reachesTheRouteAsThatAccountOnly() throws Exception {
        // the route's own check compares this account with the body's accountId (OrderServiceImpl)
        String token = new StrategyTokenIssuer(strategyTokenProperties, Clock.systemUTC()).issue(7L, 202L);
        when(orderService.placeOrder(eq(202L), any())).thenThrow(new com.team5.domain.exception.AccountAccessDeniedException());

        placeOrderWith(token).andExpect(status().isForbidden());
        verify(orderService).placeOrder(eq(202L), any());
    }

    private TradeApiOrderClient orderClientThroughTheFilterChain() {
        RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost")
            .requestFactory(new MockMvcClientHttpRequestFactory(mockMvc))
            .build();
        return new TradeApiOrderClient(restClient, new StrategyTokenIssuer(strategyTokenProperties, Clock.systemUTC()));
    }

    private org.springframework.test.web.servlet.ResultActions placeOrderWith(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"accountId":101,"symbol":"INFY.NS","side":"BUY","quantity":10,"price":1515.51,
                 "idempotencyKey":"strat-7-test-key"}"""));
    }

    // a well-formed strategy token, changed by `adjust`, signed with `secret`
    private static String token(String secret, Consumer<JwtClaimsSet.Builder> adjust) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(StrategyTokenProperties.ISSUER)
            .subject("strategy:7")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .claim("accountId", OWNER)
            .claim("scope", "orders:place");
        adjust.accept(claims);
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
            .getTokenValue();
    }
}
