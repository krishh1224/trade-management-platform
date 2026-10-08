package com.team5.watchlists.domain;

import com.team5.notifications.api.AlertDirection;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// One row of price_alerts.
public class PriceAlertRow {

    private Long alertId;
    private Long accountId;
    private Long watchlistId;
    private String symbol;
    private AlertDirection direction;
    private BigDecimal threshold;
    private PriceAlertStatus status;
    // idempotency key handed to notifications: one message per alert, however often it is seen
    private UUID deliveryId;
    private OffsetDateTime createdAt;
    private OffsetDateTime triggeredAt;
    private BigDecimal triggerPrice;

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getWatchlistId() { return watchlistId; }
    public void setWatchlistId(Long watchlistId) { this.watchlistId = watchlistId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public AlertDirection getDirection() { return direction; }
    public void setDirection(AlertDirection direction) { this.direction = direction; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public PriceAlertStatus getStatus() { return status; }
    public void setStatus(PriceAlertStatus status) { this.status = status; }

    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID deliveryId) { this.deliveryId = deliveryId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(OffsetDateTime triggeredAt) { this.triggeredAt = triggeredAt; }

    public BigDecimal getTriggerPrice() { return triggerPrice; }
    public void setTriggerPrice(BigDecimal triggerPrice) { this.triggerPrice = triggerPrice; }
}
