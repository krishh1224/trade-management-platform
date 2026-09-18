package com.team5.controller;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.kafka.OrderEventPublisher;
import com.team5.service.OrderService;
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
    private final OrderEventPublisher orderEventPublisher;

    public OrderController(OrderService orderService, OrderEventPublisher orderEventPublisher) {
        this.orderService = orderService;
        this.orderEventPublisher = orderEventPublisher;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
        @Valid @RequestBody PlaceOrderRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        // placeOrder is @Transactional — transaction commits when it returns
        OrderResponse response = orderService.placeOrder(authenticatedAccountId, request);

        // publish AFTER commit — choosing the recoverable failure
        // if publish fails, the order exists in DB and can be replayed
        // if we published inside the transaction and it rolled back, the event can't be undone
        orderEventPublisher.publishOrderPlaced(
            request,
            orderService.getLastOrderUuid(),
            orderService.getLastOrderCreatedOn()
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
        @PathVariable("id") UUID id,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(orderService.cancelOrder(authenticatedAccountId, id));
    }
}