package com.team5.strategies.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// One row of the strategies table.
public class StrategyRow {

    private Long strategyId;
    private Long accountId;
    private String name;
    private String symbol;
    private StrategyType strategyType;
    private Integer shortWindow;
    private Integer longWindow;
    private Integer bandWindow;
    private BigDecimal bandWidth;
    private Integer quantity;
    private BigDecimal maxSpend;
    private Long maxPosition;
    private Integer maxConsecutiveFailures;
    private StrategyStatus status;
    private String statusReason;
    private BigDecimal spent;
    private Integer ordersPlaced;
    private Integer consecutiveFailures;
    private OffsetDateTime lastTriggeredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public StrategyRow() {
    }

    // what is left of the spending cap
    public BigDecimal remainingSpend() {
        return maxSpend.subtract(spent == null ? BigDecimal.ZERO : spent);
    }

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public StrategyType getStrategyType() { return strategyType; }
    public void setStrategyType(StrategyType strategyType) { this.strategyType = strategyType; }

    public Integer getShortWindow() { return shortWindow; }
    public void setShortWindow(Integer shortWindow) { this.shortWindow = shortWindow; }

    public Integer getLongWindow() { return longWindow; }
    public void setLongWindow(Integer longWindow) { this.longWindow = longWindow; }

    public Integer getBandWindow() { return bandWindow; }
    public void setBandWindow(Integer bandWindow) { this.bandWindow = bandWindow; }

    public BigDecimal getBandWidth() { return bandWidth; }
    public void setBandWidth(BigDecimal bandWidth) { this.bandWidth = bandWidth; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getMaxSpend() { return maxSpend; }
    public void setMaxSpend(BigDecimal maxSpend) { this.maxSpend = maxSpend; }

    public Long getMaxPosition() { return maxPosition; }
    public void setMaxPosition(Long maxPosition) { this.maxPosition = maxPosition; }

    public Integer getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public void setMaxConsecutiveFailures(Integer maxConsecutiveFailures) { this.maxConsecutiveFailures = maxConsecutiveFailures; }

    public StrategyStatus getStatus() { return status; }
    public void setStatus(StrategyStatus status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public BigDecimal getSpent() { return spent; }
    public void setSpent(BigDecimal spent) { this.spent = spent; }

    public Integer getOrdersPlaced() { return ordersPlaced; }
    public void setOrdersPlaced(Integer ordersPlaced) { this.ordersPlaced = ordersPlaced; }

    public Integer getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(Integer consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public OffsetDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(OffsetDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
