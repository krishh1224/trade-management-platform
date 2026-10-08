package com.team5.strategies.engine;

import com.team5.domain.enums.OrderSide;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.strategies.config.StrategyProperties;
import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.PositionSnapshot;
import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyQuote;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyReadMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one fired signal into at most one order, under every control, at the point of decision.
 *
 * <pre>
 *   ── one transaction ──
 *   SELECT strategy ... FOR UPDATE            a disable or delete now waits for this decision to finish
 *   already acted on this quote?   → nothing (redelivery / replay)
 *   not ENABLED any more?          → SKIPPED_DISABLED, no order
 *   BUY:  value > remaining spend? → SKIPPED_SPEND_LIMIT
 *         held + pending + qty > max_position? → SKIPPED_POSITION_LIMIT
 *   SELL: nothing left to sell?    → SKIPPED_NO_HOLDING, else at most what is left
 *   POST /api/v1/orders with a delegated token (TradeApiOrderClient)
 *   PLACED  → spent += value, failure run cleared
 *   FAILED  → failure run + 1, HALTED when it reaches max_consecutive_failures
 *   strategy_executions row written
 *   ── commit: the strategy row is released ──
 * </pre>
 *
 * The status the engine saw when it loaded the strategy is not trusted: it is read again here, under
 * the lock, immediately before the order, so a strategy disabled mid-cycle places nothing.
 */
@Service
public class StrategyExecutor {

    private static final Logger log = LoggerFactory.getLogger(StrategyExecutor.class);

    static final String IDEMPOTENCY_PREFIX = "strat-";
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final int MAX_DETAIL = 300;
    private static final int MAX_REASON = 200;

    private final StrategyMapper strategyMapper;
    private final StrategyExecutionMapper executionMapper;
    private final StrategyReadMapper readMapper;
    private final TradeApiOrderClient orderClient;
    private final BigDecimal priceTolerance;
    private final Clock clock;

    public StrategyExecutor(
        StrategyMapper strategyMapper,
        StrategyExecutionMapper executionMapper,
        StrategyReadMapper readMapper,
        TradeApiOrderClient orderClient,
        StrategyProperties properties,
        Clock clock
    ) {
        this.strategyMapper = strategyMapper;
        this.executionMapper = executionMapper;
        this.readMapper = readMapper;
        this.orderClient = orderClient;
        this.priceTolerance = properties.priceTolerance();
        this.clock = clock;
    }

    // null when the strategy is gone or this quote was already acted on for it
    @Transactional
    public ExecutionRow execute(long strategyId, SignalResult signal, StrategyQuote quote) {
        if (!signal.fired()) {
            throw new IllegalArgumentException("execute needs a fired signal");
        }
        StrategyRow strategy = strategyMapper.lockById(strategyId);
        if (strategy == null) {
            log.info("Strategy {} was deleted before its {} signal could be acted on", strategyId, signal.side());
            return null;
        }
        if (executionMapper.exists(strategyId, quote.eventId())) {
            log.debug("Strategy {} already acted on quote {}", strategyId, quote.eventId());
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        ExecutionRow execution = new ExecutionRow();
        execution.setStrategyId(strategyId);
        execution.setQuoteEventId(quote.eventId());
        execution.setSignal(signal.side());
        execution.setQuotePrice(quote.price());
        execution.setCreatedAt(now);

        if (strategy.getStatus() != StrategyStatus.ENABLED) {
            finish(execution, ExecutionOutcome.SKIPPED_DISABLED, "Not placed: the strategy was "
                + strategy.getStatus() + " when the order was about to be sent. Signal: " + signal.reason());
            log.info("Strategy {} fired {} but is {} now; no order", strategyId, signal.side(), strategy.getStatus());
        } else {
            decideAndPlace(strategy, signal, quote, execution, now);
        }

        executionMapper.insert(execution);
        return execution;
    }

    private void decideAndPlace(
        StrategyRow strategy, SignalResult signal, StrategyQuote quote, ExecutionRow execution, OffsetDateTime now
    ) {
        OrderSide side = signal.side();
        PositionSnapshot position = readMapper.findPosition(strategy.getAccountId(), strategy.getSymbol());
        if (position == null) {
            position = new PositionSnapshot();
        }
        BigDecimal limitPrice = limitPrice(side, quote);
        execution.setOrderPrice(limitPrice);

        int quantity;
        BigDecimal value = BigDecimal.ZERO;
        if (side == OrderSide.BUY) {
            quantity = strategy.getQuantity();
            value = limitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.CEILING);
            execution.setQuantity(quantity);
            BigDecimal remaining = strategy.remainingSpend();
            if (value.compareTo(remaining) > 0) {
                skipped(strategy, execution, ExecutionOutcome.SKIPPED_SPEND_LIMIT, "Not placed: order value "
                    + value.toPlainString() + " is more than the " + remaining.toPlainString() + " left of the "
                    + strategy.getMaxSpend().toPlainString() + " spending limit", now);
                return;
            }
            long after = position.committedLong() + quantity;
            if (after > strategy.getMaxPosition()) {
                skipped(strategy, execution, ExecutionOutcome.SKIPPED_POSITION_LIMIT, "Not placed: the position would be "
                    + after + " (held " + position.getHeld() + ", pending buys " + position.getPendingBuy()
                    + ", this order " + quantity + "), over the limit of " + strategy.getMaxPosition(), now);
                return;
            }
        } else {
            long available = position.availableToSell();
            if (available <= 0) {
                skipped(strategy, execution, ExecutionOutcome.SKIPPED_NO_HOLDING, "Not placed: nothing to sell (held "
                    + position.getHeld() + ", pending sells " + position.getPendingSell() + ")", now);
                return;
            }
            quantity = (int) Math.min(strategy.getQuantity(), available);
            execution.setQuantity(quantity);
        }

        String idempotencyKey = IDEMPOTENCY_PREFIX + strategy.getStrategyId() + "-" + quote.eventId();
        execution.setIdempotencyKey(idempotencyKey);
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(strategy.getAccountId());
        request.setSymbol(strategy.getSymbol());
        request.setSide(side);
        request.setQuantity(quantity);
        request.setPrice(limitPrice);
        request.setIdempotencyKey(idempotencyKey);

        OrderPlacement placement = orderClient.place(strategy.getStrategyId(), request);
        switch (placement.result()) {
            case ACCEPTED -> placed(strategy, execution, placement.orderId(),
                side + " " + quantity + " " + strategy.getSymbol() + " at limit " + limitPrice.toPlainString()
                    + ". " + signal.reason(), value, now);
            case DUPLICATE, UNCERTAIN -> {
                // an earlier attempt may have created the order: the Trade API stored it under our key if so
                String existing = readMapper.findOrderIdByIdempotencyKey(idempotencyKey);
                if (existing != null) {
                    placed(strategy, execution, existing, "Already placed under " + idempotencyKey
                        + " (" + placement.detail() + "). " + signal.reason(), value, now);
                } else if (placement.result() == OrderPlacement.Result.DUPLICATE) {
                    failed(strategy, execution, placement.detail() + "; no order holds this key", BigDecimal.ZERO, now);
                } else {
                    // it may still appear: count its value against the limit rather than risk spending twice
                    failed(strategy, execution, placement.detail(), value, now);
                }
            }
            case REJECTED -> failed(strategy, execution, placement.detail(), BigDecimal.ZERO, now);
        }
    }

    // BUY a little above the ask, SELL a little below the bid, so the executor's later quote still fills
    BigDecimal limitPrice(OrderSide side, StrategyQuote quote) {
        if (side == OrderSide.BUY) {
            BigDecimal reference = positive(quote.ask()) ? quote.ask() : quote.price();
            return reference.multiply(BigDecimal.ONE.add(priceTolerance)).setScale(2, RoundingMode.CEILING);
        }
        BigDecimal reference = positive(quote.bid()) ? quote.bid() : quote.price();
        BigDecimal limit = reference.multiply(BigDecimal.ONE.subtract(priceTolerance)).setScale(2, RoundingMode.FLOOR);
        return limit.max(MIN_PRICE);
    }

    private void placed(
        StrategyRow strategy, ExecutionRow execution, String orderId, String detail, BigDecimal value, OffsetDateTime now
    ) {
        execution.setOrderId(orderId);
        finish(execution, ExecutionOutcome.PLACED, detail);
        strategyMapper.recordPlaced(strategy.getStrategyId(), value, now);
        log.info("Strategy {} placed {} {} {} for account {}: order {}", strategy.getStrategyId(),
            execution.getSignal(), execution.getQuantity(), strategy.getSymbol(), strategy.getAccountId(), orderId);
    }

    private void failed(
        StrategyRow strategy, ExecutionRow execution, String detail, BigDecimal reservedValue, OffsetDateTime now
    ) {
        finish(execution, ExecutionOutcome.FAILED, detail);
        int failuresInARow = strategy.getConsecutiveFailures() + 1;
        boolean halts = failuresInARow >= strategy.getMaxConsecutiveFailures();
        String haltReason = halts
            ? truncate("Halted after " + failuresInARow + " failed orders in a row. Last: " + detail, MAX_REASON)
            : null;
        strategyMapper.recordFailure(strategy.getStrategyId(), reservedValue, haltReason, now);
        if (halts) {
            log.warn("Strategy {} for account {} HALTED: {}", strategy.getStrategyId(), strategy.getAccountId(), haltReason);
        } else {
            log.warn("Strategy {} order failed ({} of {} in a row): {}", strategy.getStrategyId(),
                failuresInARow, strategy.getMaxConsecutiveFailures(), detail);
        }
    }

    private void skipped(
        StrategyRow strategy, ExecutionRow execution, ExecutionOutcome outcome, String detail, OffsetDateTime now
    ) {
        finish(execution, outcome, detail);
        strategyMapper.recordTriggered(strategy.getStrategyId(), now);
        log.info("Strategy {} fired {} but was stopped by a limit: {}", strategy.getStrategyId(), execution.getSignal(), detail);
    }

    private static void finish(ExecutionRow execution, ExecutionOutcome outcome, String detail) {
        execution.setOutcome(outcome);
        execution.setDetail(truncate(detail, MAX_DETAIL));
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
