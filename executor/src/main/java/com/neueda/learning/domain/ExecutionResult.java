package com.neueda.learning.domain;

import com.neueda.learning.domain.enums.ExecutionStatus;
import java.math.BigDecimal;

/**
 * Result of trade execution: contains decision, order info, and reason.
 * Used by the service to determine final order resolution (FILLED/REJECTED).
 */
public class ExecutionResult {
    private final Long orderId;
    private final ExecutionStatus status;
    private final BigDecimal executionPrice;
    private final String reason;

    public ExecutionResult(Long orderId, ExecutionStatus status, BigDecimal executionPrice, String reason) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.orderId = orderId;
        this.status = status;
        this.executionPrice = executionPrice;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public String getReason() {
        return reason;
    }

    public boolean isFilled() {
        return status == ExecutionStatus.FILLED;
    }

    public boolean isRejected() {
        return status == ExecutionStatus.REJECTED;
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "orderId=" + orderId +
                ", status=" + status +
                ", executionPrice=" + executionPrice +
                ", reason='" + reason + '\'' +
                '}';
    }
}
