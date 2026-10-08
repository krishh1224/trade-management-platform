package com.team5.strategies.engine;

import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.ErrorResponse;
import com.team5.dto.response.OrderResponse;
import com.team5.strategies.config.StrategyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Places a strategy's order through the Trade API's own route, POST /api/v1/orders, over HTTP. Never
 * the OrderService bean and never the orders topic, although both are in this process: the route's
 * validation, its accountId check against the token and its idempotency key are the controls between
 * a strategy defect and a real position, and calling past them would skip all three.
 */
@Component
public class TradeApiOrderClient {

    private static final Logger log = LoggerFactory.getLogger(TradeApiOrderClient.class);

    static final String ORDERS_PATH = "/api/v1/orders";
    private static final String DUPLICATE_ORDER_CODE = "ORD-409";

    private final RestClient restClient;
    private final StrategyTokenIssuer tokenIssuer;

    @Autowired
    public TradeApiOrderClient(StrategyProperties properties, StrategyTokenIssuer tokenIssuer) {
        this(restClient(properties), tokenIssuer);
    }

    TradeApiOrderClient(RestClient restClient, StrategyTokenIssuer tokenIssuer) {
        this.restClient = restClient;
        this.tokenIssuer = tokenIssuer;
    }

    private static RestClient restClient(StrategyProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        return RestClient.builder()
            .baseUrl(properties.tradeApiBaseUrl())
            .requestFactory(factory)
            .build();
    }

    public OrderPlacement place(long strategyId, PlaceOrderRequest request) {
        if (!tokenIssuer.configured()) {
            // fail closed: without its own key the module has no identity to trade with
            return OrderPlacement.rejected(0, null,
                "No strategy identity is configured (STRATEGY_TOKEN_SECRET); no order was sent");
        }
        String token = tokenIssuer.issue(strategyId, request.getAccountId());
        try {
            OrderResponse response = restClient.post()
                .uri(ORDERS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OrderResponse.class);
            String orderId = response == null ? null : response.orderId();
            return OrderPlacement.accepted(200, orderId);
        } catch (HttpClientErrorException e) {
            ErrorResponse error = errorBody(e);
            String code = error == null ? null : error.errorCode();
            String detail = "Trade API " + e.getStatusCode().value()
                + (code == null ? "" : " " + code)
                + (error == null || error.message() == null ? "" : ": " + error.message());
            if (e.getStatusCode().value() == 409 && DUPLICATE_ORDER_CODE.equals(code)) {
                return OrderPlacement.duplicate(code, detail);
            }
            return OrderPlacement.rejected(e.getStatusCode().value(), code, detail);
        } catch (HttpServerErrorException e) {
            return OrderPlacement.uncertain(e.getStatusCode().value(), "Trade API " + e.getStatusCode().value()
                + "; the order may or may not have been placed");
        } catch (RestClientException e) {
            // connection refused, timeout, unreadable response: nothing says whether the order exists
            log.warn("Order for strategy {} got no usable answer from the Trade API: {}", strategyId, e.getMessage());
            return OrderPlacement.uncertain(0, "No answer from the Trade API ("
                + e.getClass().getSimpleName() + "); the order may or may not have been placed");
        }
    }

    private ErrorResponse errorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(ErrorResponse.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
