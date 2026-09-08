
package com.team5.domain.rules;

import com.team5.domain.Account;
import com.team5.domain.Instrument;
import com.team5.domain.PortfolioHolding;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.DuplicateOrderException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.domain.exception.InsufficientFundsException;
import com.team5.domain.exception.InsufficientHoldingsException;

import java.math.BigDecimal;

/**
 * Validates an order against all the required business rules.
 */
public class OrderValidator {

    private final IdempotencyChecker idempotencyChecker;

    public OrderValidator(IdempotencyChecker idempotencyChecker) {
        if (idempotencyChecker == null) {
            throw new IllegalArgumentException("idempotencyChecker must not be null.");
        }
        this.idempotencyChecker = idempotencyChecker;
    }

    /**
     * Checks the order rules in order and throws an exception if any rule fails.
     */
    public void validate(Account account,
                          Instrument instrument,
                          PortfolioHolding holding,
                          OrderSide side,
                          int quantity,
                          BigDecimal price,
                          String idempotencyKey) {

        if (side == null) {
            throw new IllegalArgumentException("side must not be null.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank.");
        }

        // Check that the account exists.
        if (account == null) {
            throw new AccountNotFoundException();
        }

        // Check that the account is active.
        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }

        // Check that the instrument exists and can be traded.
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException();
        }

        // Check that the quantity is positive.
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }

        // Check that the price is positive.
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive.");
        }

        // Check if the account has enough money for a BUY.
        if (side == OrderSide.BUY) {
            BigDecimal cost = price.multiply(quantity);
            if (!account.canAfford(cost)) {
                throw new InsufficientFundsException();
            }
        }

        // Check if the account has enough holdings for a SELL.
        if (side == OrderSide.SELL) {
            boolean holdsEnough = holding != null && holding.hasEnough(quantity);
            if (!holdsEnough) {
                throw new InsufficientHoldingsException();
            }
        }

        // Check that the idempotency key has not already been used.
        if (idempotencyChecker.isDuplicate(idempotencyKey)) {
            throw new DuplicateOrderException();
        }
    }
}

