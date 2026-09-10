package com.team5.service;

import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.mapper.OrderCommandMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderCommandMapper orderCommandMapper;

    public OrderServiceImpl(OrderCommandMapper orderCommandMapper) {
        this.orderCommandMapper = orderCommandMapper;
    }

    @Override
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        String generatedOrderId = "ORD-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        int insertedRows = orderCommandMapper.insertOrder(
            generatedOrderId,
            request.getAccountId(),
            request.getSymbol(),
            request.getSide().name(),
            request.getQuantity(),
            request.getPrice(),
            "NEW",
            request.getIdempotencyKey(),
            now,
            now
        );

        if (insertedRows != 1) {
            throw new IllegalStateException("Expected one row to be inserted for order " + generatedOrderId);
        }

        return orderCommandMapper.findOrderByOrderId(generatedOrderId);
    }

    @Override
    public OrderResponse cancelOrder(UUID orderId) {
        String persistentOrderId = "ORD-" + orderId;
        int updatedRows = orderCommandMapper.updateOrderStatus(
            persistentOrderId,
            "CANCELLED",
            OffsetDateTime.now()
        );

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected one row to be updated for order " + persistentOrderId);
        }

        return orderCommandMapper.findOrderByOrderId(persistentOrderId);
    }
}
