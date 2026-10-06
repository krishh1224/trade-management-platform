package com.neueda.learning.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload for ORDER_PLACED events on the orders topic.
 * Imported from trade-api contract.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPlacedPayload {

    @JsonProperty("orderId")
    private UUID orderId;

    @JsonProperty("accountId")
    private long accountId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side;

    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @JsonProperty("createdOn")
    private OffsetDateTime createdOn;

    public OrderPlacedPayload() {
    }

    public OrderPlacedPayload(UUID orderId, long accountId, String symbol, String side,
                              int quantity, BigDecimal price, String idempotencyKey,
                              OffsetDateTime createdOn) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
        this.createdOn = createdOn;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public OffsetDateTime getCreatedOn() { return createdOn; }
    public void setCreatedOn(OffsetDateTime createdOn) { this.createdOn = createdOn; }

    @Override
    public String toString() {
        return "OrderPlacedPayload{" +
                "orderId=" + orderId +
                ", accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", createdOn=" + createdOn +
                '}';
    }
}
