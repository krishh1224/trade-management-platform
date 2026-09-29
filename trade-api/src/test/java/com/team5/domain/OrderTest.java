package com.team5.domain;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateNewOrder() {
        Order order = createOrder(OrderStatus.NEW);

        assertEquals(OrderStatus.NEW, order.getStatus());
        assertTrue(validator.validate(order).isEmpty());
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
        Order order = new Order(
                1L,
                1L,
                1L,
                0,
                new BigDecimal("100"),
                OrderStatus.NEW,
                OrderSide.BUY,
                "ORDER123",
                null,
                null,
                null
        );

        Set<ConstraintViolation<Order>> violations =
                validator.validate(order);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"))
        );
    }

    @Test
    void gettersReturnConstructorValues() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Order order = new Order(
                1L, 2L, 3L, 10, new BigDecimal("100"),
                OrderStatus.NEW, OrderSide.BUY, "ORDER123",
                now, now, now
        );

        assertEquals(1L, order.getOrderId());
        assertEquals(2L, order.getPortfolioId());
        assertEquals(3L, order.getInstrumentId());
        assertEquals(10, order.getQuantity());
        assertEquals(new BigDecimal("100"), order.getPrice());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals("ORDER123", order.getIdempotencyKey());
        assertEquals(now, order.getCreatedAt());
        assertEquals(now, order.getUpdatedAt());
        assertEquals(now, order.getExpiresAt());
    }

    @Test
    void toStringContainsKeyFields() {
        Order order = createOrder(OrderStatus.NEW);

        String text = order.toString();

        assertTrue(text.contains("orderId=1"));
        assertTrue(text.contains("side=BUY"));
        assertTrue(text.contains("status=NEW"));
        assertTrue(text.contains("idempotencyKey='ORDER123'"));
    }

    private Order createOrder(OrderStatus status) {
        return new Order(
                1L,
                1L,
                1L,
                10,
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