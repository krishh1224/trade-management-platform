package com.team5.controller;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.domain.OrderIdentifiers;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.kafka.OrderEventPublisher;
import com.team5.notifications.service.NotificationService;
import com.team5.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderEventPublisher orderEventPublisher;
    private final NotificationService notificationService;

    public OrderController(
        OrderService orderService,
        OrderEventPublisher orderEventPublisher,
        NotificationService notificationService
    ) {
        this.orderService = orderService;
        this.orderEventPublisher = orderEventPublisher;
        this.notificationService = notificationService;
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
        // cancelOrder is @Transactional and only returns once the cancel has committed
        OrderResponse response = orderService.cancelOrder(authenticatedAccountId, id);

        // published AFTER commit to the orders topic; the executor confirms it against the database
        // and announces it on trade-events, which the notifications module consumes
        orderEventPublisher.publishOrderCancelled(authenticatedAccountId, id, response);

        // tell the customer now rather than when the executor gets to it
        notifyCancelled(authenticatedAccountId, id, response);

        return ResponseEntity.ok(response);
    }

    // The cancel is committed, so this cannot be a false announcement. It shares its eventId with the
    // executor's later ORDER_CANCELLED, so the customer gets one notification whichever arrives first.
    // A failure here is only logged: the cancel has succeeded and the executor's path still notifies.
    private void notifyCancelled(long accountId, UUID orderId, OrderResponse order) {
        try {
            notificationService.recordCancelledOrder(
                accountId,
                OrderIdentifiers.persistentId(orderId),
                order.symbol(),
                order.side() == null ? null : order.side().name(),
                order.quantity(),
                order.price()
            );
        } catch (RuntimeException e) {
            log.warn("Could not record the cancel notification for order {} now; the executor's event will: {}",
                orderId, e.getMessage());
        }
    }
}
