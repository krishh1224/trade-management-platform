package com.team5.domain.exception;

import java.math.BigDecimal;

public class InsufficientHoldingsException extends TradeDomainException {

    private final BigDecimal currentQuantity;
    private final BigDecimal requestedQuantity;

    public InsufficientHoldingsException(BigDecimal currentQuantity, BigDecimal requestedQuantity) {
        super("Insufficient holdings: holding is " + currentQuantity.toPlainString()
            + " units but sell of " + requestedQuantity.toPlainString() + " units was requested.");
        this.currentQuantity   = currentQuantity;
        this.requestedQuantity = requestedQuantity;
    }

    public BigDecimal getCurrentQuantity()   { return currentQuantity; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
}