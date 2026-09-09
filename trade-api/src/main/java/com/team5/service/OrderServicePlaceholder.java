package com.team5.service;

import com.team5.domain.enums.OrderStatus;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderServicePlaceholder implements OrderService {

    @Override
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        String generatedOrderId = "ORD-" + UUID.randomUUID();
        return new OrderResponse(
            generatedOrderId,
            OrderStatus.NEW,
            "Order accepted",
            request.getSymbol(),
            request.getSide(),
            request.getQuantity(),
            request.getPrice()
        );
    }

    @Override
    public OrderResponse cancelOrder(UUID orderId) {
        return new OrderResponse(
            "ORD-" + orderId,
            OrderStatus.CANCELLED,
            "Order cancelled",
            "ACME",
            com.team5.domain.enums.OrderSide.BUY,
            100,
            java.math.BigDecimal.valueOf(25.50)
        );
    }
}
