package com.neueda.learning.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Database record for Order entity.
 * Used for persistence layer mapping.
 */
public class OrderRecord {
    private Long orderId;
    private Long portfolioId;
    private Long instrumentId;
    private Integer quantity;
    private BigDecimal price;
    private String status;
    private String side;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    // Constructors
    public OrderRecord() {
    }

    public OrderRecord(Long orderId, Long portfolioId, Long instrumentId, Integer quantity,
                       BigDecimal price, String status, String side, String idempotencyKey,
                       LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) {
        this.orderId = orderId;
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.side = side;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "OrderRecord{" +
                "orderId=" + orderId +
                ", portfolioId=" + portfolioId +
                ", instrumentId=" + instrumentId +
                ", quantity=" + quantity +
                ", price=" + price +
                ", status='" + status + '\'' +
                ", side='" + side + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}

