package com.team5.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Payload for ORDER_FILLED, ORDER_REJECTED, ORDER_CANCELLED on trade-events topic
// Key: accountId as a string | Source: trade-executor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeEventPayload {

    @JsonProperty("orderId")
    private UUID orderId;

    @JsonProperty("accountId")
    private long accountId; // also used as the kafka message key

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side; // BUY or SELL

    @JsonProperty("quantity")
    private int quantity; // equals ordered quantity, no partial fills

    @JsonProperty("price")
    private BigDecimal price; // limit price from the original order

    @JsonProperty("executedPrice")
    private BigDecimal executedPrice; // quote price the fill settled at, null on reject/cancel

    @JsonProperty("status")
    private String status; // FILLED, REJECTED, or CANCELLED

    @JsonProperty("reason")
    private String reason; // null on fill, e.g. INSUFFICIENT_FUNDS or PRICE_NOT_MET on reject

    @JsonProperty("cashDelta")
    private BigDecimal cashDelta; // signed change to cash, negative for a buy

    @JsonProperty("positionQuantityAfter")
    private int positionQuantityAfter; // holdings after this event

    @JsonProperty("averageCostAfter")
    private BigDecimal averageCostAfter; // weighted avg cost after this event

    @JsonProperty("executedOn")
    private OffsetDateTime executedOn;

    public TradeEventPayload() {
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

    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getCashDelta() { return cashDelta; }
    public void setCashDelta(BigDecimal cashDelta) { this.cashDelta = cashDelta; }

    public int getPositionQuantityAfter() { return positionQuantityAfter; }
    public void setPositionQuantityAfter(int positionQuantityAfter) { this.positionQuantityAfter = positionQuantityAfter; }

    public BigDecimal getAverageCostAfter() { return averageCostAfter; }
    public void setAverageCostAfter(BigDecimal averageCostAfter) { this.averageCostAfter = averageCostAfter; }

    public OffsetDateTime getExecutedOn() { return executedOn; }
    public void setExecutedOn(OffsetDateTime executedOn) { this.executedOn = executedOn; }
}