package com.team5.service;

import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface OrderService {

    OrderResponse placeOrder(long authenticatedAccountId, PlaceOrderRequest request);

    OrderResponse cancelOrder(long authenticatedAccountId, UUID orderId);

    // details of the most recently placed order, used by the controller to publish after commit
    UUID getLastOrderUuid();

    OffsetDateTime getLastOrderCreatedOn();
}
