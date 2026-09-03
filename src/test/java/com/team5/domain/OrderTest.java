package com.team5.domain;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateNewOrder() {
        Order order = createOrder(OrderStatus.NEW);

        assertEquals(OrderStatus.NEW, order.getStatus());
    }

    @Test
    void shouldFillNewOrder() {
        Order order = createOrder(OrderStatus.NEW);

        order.fill();

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertTrue(order.isTerminal());
    }

    @Test
    void shouldRejectNewOrder() {
        Order order = createOrder(OrderStatus.NEW);

        order.reject();

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertTrue(order.isTerminal());
    }

    @Test
    void shouldCancelNewOrder() {
        Order order = createOrder(OrderStatus.NEW);

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.isTerminal());
    }

    @Test
    void shouldNotAllowFilledOrderToBeCancelled() {
        Order order = createOrder(OrderStatus.NEW);
        order.fill();

        assertThrows(
                IllegalStateException.class,
                order::cancel
        );
    }

    @Test
    void shouldNotAllowRejectedOrderToBeFilled() {
        Order order = createOrder(OrderStatus.NEW);
        order.reject();

        assertThrows(
                IllegalStateException.class,
                order::fill
        );
    }

    @Test
    void shouldNotAllowCancelledOrderToBeRejected() {
        Order order = createOrder(OrderStatus.NEW);
        order.cancel();

        assertThrows(
                IllegalStateException.class,
                order::reject
        );
    }

    @Test
    void shouldRejectNonPositiveOrderQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                        1L,
                        1L,
                        1L,
                        BigDecimal.ZERO,
                        new BigDecimal("100"),
                        OrderStatus.NEW,
                        OrderSide.BUY,
                        "ORDER123",
                        null,
                        null,
                        null
                )
        );
    }

    private Order createOrder(OrderStatus status) {
        return new Order(
                1L,
                1L,
                1L,
                new BigDecimal("10"),
                new BigDecimal("100"),
                status,
                OrderSide.BUY,
                "ORDER123",
                null,
                null,
                null
        );
    }
}