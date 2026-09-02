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
 * Enforces business rules 1-8 for a prospective order, in the stated order,
 * with first-failure-wins semantics.
 *
 * This class lives in the domain, not a controller, precisely so the Trade
 * Executor (a later component) can call the same logic directly instead of
 * re-implementing it. Rules re-implemented in two places drift; the first
 * drift accepts an order the executor then refuses. There is exactly one
 * copy of this logic.
 *
 * Two rules from the ticket carry no code this sprint and are deliberately
 * NOT implemented here:
 *   - cash and position must move together, or neither moves (atomicity is
 *     an execution-layer / transaction-boundary concern, not a validation
 *     concern)
 *   - every order is recorded, including a rejected one (recording is an
 *     execution-layer side effect; validation must stay free of side
 *     effects other than the idempotency check itself)
 * Both are left for whichever ticket owns order execution/persistence.
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
     * Validates a prospective order. Throws on the first rule that fails;
     * returns normally (void) if the order passes all eight checks.
     * Nothing is mutated - not the account, not the holding, not the
     * idempotency checker - on either success or failure. Deciding whether
     * to record the idempotency key, debit the account, and adjust the
     * holding is the caller's job, once, atomically, after validation
     * passes.
     *
     * @param account        the account placing the order; null means "no
     *                       such account" (rule 1)
     * @param instrument     the instrument being traded; null means "no
     *                       such instrument" (rule 3)
     * @param holding        the account's current holding in this
     *                       instrument; null is treated as "holds zero" and
     *                       only matters for a SELL (rule 7)
     * @param side           BUY or SELL
     * @param quantity       requested quantity; re-checked here even though
     *                       a DTO validator may already have checked it,
     *                       because this method must also be safe for a
     *                       caller that never ran a validator
     * @param price          requested price; re-checked for the same reason
     * @param idempotencyKey the client-supplied idempotency key
     */
    public void validate(Account account,
                          Instrument instrument,
                          PortfolioHolding holding,
                          OrderSide side,
                          BigDecimal quantity,
                          BigDecimal price,
                          String idempotencyKey) {

        if (side == null) {
            throw new IllegalArgumentException("side must not be null.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank.");
        }

        // Rule 1 - the account must exist.
        if (account == null) {
            throw new AccountNotFoundException();
        }

        // Rule 2 - the account must be active.
        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }

        // Rule 3 - the instrument must exist and be tradable.
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException();
        }

        // Rule 4 - quantity must be a valid, positive amount.
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }

        // Rule 5 - price must be a valid, positive amount.
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive.");
        }

        // Rule 6 - a BUY must be affordable.
        if (side == OrderSide.BUY) {
            BigDecimal cost = price.multiply(quantity);
            if (!account.canAfford(cost)) {
                throw new InsufficientFundsException();
            }
        }

        // Rule 7 - a SELL must not exceed the held quantity.
        if (side == OrderSide.SELL) {
            boolean holdsEnough = holding != null && holding.hasEnough(quantity);
            if (!holdsEnough) {
                throw new InsufficientHoldingsException();
            }
        }

        // Rule 8 - the idempotency key must not be a duplicate.
        if (idempotencyChecker.isDuplicate(idempotencyKey)) {
            throw new DuplicateOrderException();
        }
    }
}
