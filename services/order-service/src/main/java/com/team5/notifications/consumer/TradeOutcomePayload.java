package com.team5.notifications.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team5.domain.OrderIdentifiers;
import java.math.BigDecimal;
import java.util.UUID;

// What this module reads from a trade-events payload. Deliberately tolerant, because the
// producers do not agree on one shape:
//   ORDER_FILLED   (executor)  orderId as a number, accountId, symbol, side, quantity, executedPrice
//   ORDER_REJECTED (executor)  orderId as a number, accountId, rejectionReason, nothing else
//   ORDER_CANCELLED (executor) orderId as a number, accountId, symbol, side, quantity, price
// com.team5.kafka.TradeEventPayload types orderId as a UUID and would fail on the executor's events.
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeOutcomePayload {

    @JsonProperty("orderId")
    private String orderId; // number, UUID or "ORD-<uuid>"; Jackson coerces a JSON number to text

    @JsonProperty("accountId")
    private Long accountId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("executedPrice")
    private BigDecimal executedPrice;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("rejectionReason")
    private String rejectionReason;

    public TradeOutcomePayload() {
    }

    // orders.order_id for any of the three id formats, or null when absent or unreadable
    public Long persistentOrderId() {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        String value = orderId.trim();
        if (value.startsWith("ORD-")) {
            value = value.substring(4);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notNumeric) {
            try {
                return OrderIdentifiers.persistentId(UUID.fromString(value));
            } catch (IllegalArgumentException notUuid) {
                return null;
            }
        }
    }

    public String reasonCode() {
        return rejectionReason != null ? rejectionReason : reason;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
