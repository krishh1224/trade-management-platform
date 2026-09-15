package com.team5.service;

import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import java.util.UUID;

public interface OrderService {

    OrderResponse placeOrder(long authenticatedAccountId, PlaceOrderRequest request);

    OrderResponse cancelOrder(long authenticatedAccountId, UUID orderId);
}
