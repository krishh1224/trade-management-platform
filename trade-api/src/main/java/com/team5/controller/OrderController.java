package com.team5.controller;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.RequestAuthContext;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public OrderController(OrderService orderService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.orderService = orderService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request, HttpServletRequest httpRequest) {
        accountAccessAuthorizer.assertAuthorised(
            RequestAuthContext.requireAuthenticatedAccountId(httpRequest),
            request.getAccountId()
        );
        return ResponseEntity.ok(orderService.placeOrder(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
