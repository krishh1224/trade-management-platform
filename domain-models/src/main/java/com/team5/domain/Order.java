package com.team5.domain;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {

    @NotNull
    private final Long orderId;

    @NotNull
    private final Long portfolioId;

    @NotNull
    private final Long instrumentId;

    @NotNull
    @Positive
    private final BigDecimal quantity;

    @NotNull
    @Positive
    private final BigDecimal price;

    @NotNull
    private OrderStatus status;

    @NotNull
    private final OrderSide side;

    @NotBlank
    private final String idempotencyKey;

    private final LocalDateTime createdAt;

    private final LocalDateTime updatedAt;

    private final LocalDateTime expiresAt;

    public Order(Long orderId, Long portfolioId, Long instrumentId,
                 BigDecimal quantity, BigDecimal price, OrderStatus status,
                 OrderSide side, String idempotencyKey,
                 LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) {

        this.orderId        = orderId;
        this.portfolioId    = portfolioId;
        this.instrumentId   = instrumentId;
        this.quantity       = quantity;
        this.price          = price;
        this.status         = status;
        this.side           = side;
        this.idempotencyKey = idempotencyKey;
        this.createdAt      = createdAt;
        this.updatedAt      = updatedAt;
        this.expiresAt      = expiresAt;
    }

    public void fill() {
        requireNEW(OrderStatus.FILLED);
        this.status = OrderStatus.FILLED;
    }

    public void reject() {
        requireNEW(OrderStatus.REJECTED);
        this.status = OrderStatus.REJECTED;
    }

    public void cancel() {
        requireNEW(OrderStatus.CANCELLED);
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isTerminal() { return status.isTerminal(); }

    public Long getOrderId()          { return orderId; }
    public Long getPortfolioId()      { return portfolioId; }
    public Long getInstrumentId()     { return instrumentId; }
    public BigDecimal getQuantity()   { return quantity; }
    public BigDecimal getPrice()      { return price; }
    public OrderStatus getStatus()    { return status; }
    public OrderSide getSide()        { return side; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }
    public LocalDateTime getExpiresAt()  { return expiresAt; }

    private void requireNEW(OrderStatus target) {
        if (this.status != OrderStatus.NEW) {
            throw new IllegalStateException(
                "Order transition not allowed: cannot move from " + this.status + " to " + target + ".");
        }
    }

    @Override
    public String toString() {
        return "Order{orderId=" + orderId + ", side=" + side + ", status=" + status
            + ", quantity=" + quantity + ", price=" + price
            + ", idempotencyKey='" + idempotencyKey + "'}";
    }
}