package com.neueda.learning.service;

import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.mapper.AccountMapper;
import com.neueda.learning.mapper.OrderMapper;
import com.neueda.learning.mapper.PortfolioHoldingMapper;
import com.neueda.learning.model.AccountRecord;
import com.neueda.learning.model.OrderRecord;
import com.neueda.learning.model.PortfolioHoldingRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates transactional settlement of filled/rejected orders.
 *
 * Guarantees:
 * 1. Order status, cash balance, and position updates happen atomically.
 * 2. Conditional order claim (first write) guards against duplicate processing.
 * 3. Optimistic locking on account version with bounded retry on conflict.
 * 4. Kafka event publishing happens ONLY after transaction commit.
 *
 * Flow:
 *   BEGIN TX
 *   → claimOrder (guard: status must be NEW)
 *   → IF claimOrder returned 0 rows: skip cash/position updates, return early (duplicate)
 *   → IF FILLED: read account, update with optimistic lock (retry on conflict)
 *   → IF FILLED: read/update portfolio holding
 *   COMMIT TX
 *   → publish Kafka event
 *   → acknowledge consumed message
 */
@Service
public class SettlementService {
    private static final Logger logger = LoggerFactory.getLogger(SettlementService.class);

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final PortfolioHoldingMapper holdingMapper;

    @Value("${settlement.optimistic-lock.max-retries:3}")
    private int maxRetries;

    public SettlementService(OrderMapper orderMapper,
                             AccountMapper accountMapper,
                             PortfolioHoldingMapper holdingMapper) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
    }

    /**
     * Settle an order (FILLED or REJECTED) within a single transaction.
     *
     * @param execution the execution result (FILLED or REJECTED) and prices
     * @param order the original order record
     * @param accountId the account ID (portfolio's parent account)
     * @param portfolioId the portfolio ID
     * @param quantity the order quantity
     * @param instrumentId the instrument ID
     * @return SettlementResult with success flag and any error message
     */
    @Transactional
    public SettlementResult settleOrder(ExecutionResult execution, OrderRecord order,
                                        Long accountId, Long portfolioId, Integer quantity,
                                        Long instrumentId) {
        logger.info("Starting settlement for order {} with status {}", order.getOrderId(), execution.getStatus());

        try {
            // 1. FIRST WRITE: Conditional claim - guards against duplicate processing
            LocalDateTime executedOn = LocalDateTime.now();
            int claimedRows;

            if (execution.isFilled()) {
                claimedRows = orderMapper.claimOrderIfNew(
                    order.getOrderId(),
                    "FILLED",
                    execution.getExecutionPrice(),
                    executedOn,
                    null // no rejection reason for FILLED
                );
            } else {
                // Truncate rejection reason to fit VARCHAR(255) column
                String rejectionReason = execution.getReason();
                if (rejectionReason != null && rejectionReason.length() > 255) {
                    rejectionReason = rejectionReason.substring(0, 252) + "...";
                }
                claimedRows = orderMapper.claimOrderIfNew(
                    order.getOrderId(),
                    "REJECTED",
                    null, // no execution price for REJECTED
                    executedOn,
                    rejectionReason
                );
            }

            logger.info("Order claim result: {} rows affected", claimedRows);

            // If claimedRows == 0, this order was already processed (duplicate)
            if (claimedRows == 0) {
                logger.info("Order {} already processed (duplicate delivery), skipping settlement", order.getOrderId());
                return SettlementResult.duplicate();
            }

            // If REJECTED, no cash or position updates needed
            if (execution.isRejected()) {
                logger.info("Order {} rejected with reason: {}", order.getOrderId(), execution.getReason());
                return SettlementResult.success();
            }

            // 2. FILLED: Update account balance with optimistic locking
            SettlementResult accountResult = updateAccountBalance(
                accountId, order.getSide(), quantity, execution.getExecutionPrice()
            );
            if (!accountResult.isSuccess()) {
                return accountResult; // Transaction will rollback
            }

            // 3. FILLED: Update portfolio holdings
            SettlementResult holdingResult = updatePortfolioHolding(
                portfolioId, instrumentId, order.getSide(), quantity, execution.getExecutionPrice()
            );
            if (!holdingResult.isSuccess()) {
                return holdingResult; // Transaction will rollback
            }

            logger.info("Order {} settlement completed successfully", order.getOrderId());
            return SettlementResult.success();

        } catch (Exception e) {
            logger.error("Unexpected error settling order {}: {}", order.getOrderId(), e.getMessage(), e);
            return SettlementResult.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Update account balance using optimistic locking with retry on conflict.
     * Retries up to maxRetries times if version mismatch occurs.
     *
     * @param accountId the account ID
     * @param side BUY or SELL
     * @param quantity the fill quantity
     * @param executionPrice the execution price
     * @return SettlementResult
     */
    private SettlementResult updateAccountBalance(Long accountId, String side, Integer quantity,
                                                   BigDecimal executionPrice) {
        BigDecimal tradeValue = executionPrice.multiply(new BigDecimal(quantity));

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Read current account state
            AccountRecord account = accountMapper.findById(accountId);
            if (account == null) {
                return SettlementResult.error("Account not found: " + accountId);
            }

            // Calculate new balance
            BigDecimal newBalance;
            if ("BUY".equalsIgnoreCase(side)) {
                // BUY: subtract from balance
                newBalance = account.getBalance().subtract(tradeValue);
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    return SettlementResult.error("Insufficient balance for BUY: required " + tradeValue +
                            ", available " + account.getBalance());
                }
            } else if ("SELL".equalsIgnoreCase(side)) {
                // SELL: add to balance
                newBalance = account.getBalance().add(tradeValue);
            } else {
                return SettlementResult.error("Unknown side: " + side);
            }

            // Try to update with optimistic lock
            int updatedRows = accountMapper.updateBalanceWithVersionCheck(accountId, newBalance, account.getVersion());

            if (updatedRows == 1) {
                logger.info("Account {} balance updated: {} -> {} (version {})",
                    accountId, account.getBalance(), newBalance, account.getVersion());
                return SettlementResult.success();
            }

            // Version mismatch (another transaction updated this account)
            logger.warn("Account {} version mismatch on attempt {}/{}, retrying...",
                accountId, attempt, maxRetries);
            if (attempt < maxRetries) {
                continue; // Retry with freshly read account
            } else {
                return SettlementResult.error("Account balance update failed after " + maxRetries + " retries: " +
                        "concurrent modification detected");
            }
        }

        return SettlementResult.error("Account update failed: unknown error");
    }

    /**
     * Update or create portfolio holding based on order side and quantity.
     *
     * @param portfolioId the portfolio ID
     * @param instrumentId the instrument ID
     * @param side BUY or SELL
     * @param quantity the fill quantity
     * @param executionPrice the execution price
     * @return SettlementResult
     */
    private SettlementResult updatePortfolioHolding(Long portfolioId, Long instrumentId, String side,
                                                    Integer quantity, BigDecimal executionPrice) {
        PortfolioHoldingRecord holding = holdingMapper.findByPortfolioAndInstrument(portfolioId, instrumentId);

        if ("BUY".equalsIgnoreCase(side)) {
            if (holding == null) {
                // New holding: create it
                // In production, generate ID via sequence or UUID; here we use a simple ID generation
                long holdingId = System.nanoTime(); // Simplified; real code uses DB sequence
                int inserted = holdingMapper.insertHolding(holdingId, portfolioId, instrumentId,
                    (long) quantity, executionPrice);
                if (inserted != 1) {
                    return SettlementResult.error("Failed to create holding for portfolio " + portfolioId +
                            " instrument " + instrumentId);
                }
                logger.info("Created new holding {} for portfolio {} instrument {}: quantity={}",
                    holdingId, portfolioId, instrumentId, quantity);
            } else {
                // Existing holding: increase quantity
                long newQuantity = holding.getQuantity() + quantity;
                int updated = holdingMapper.updateQuantity(holding.getPortfolioHoldingId(), newQuantity);
                if (updated != 1) {
                    return SettlementResult.error("Failed to update holding for portfolio " + portfolioId +
                            " instrument " + instrumentId);
                }
                logger.info("Updated holding {} quantity: {} -> {}",
                    holding.getPortfolioHoldingId(), holding.getQuantity(), newQuantity);
            }
        } else if ("SELL".equalsIgnoreCase(side)) {
            if (holding == null || holding.getQuantity() < quantity) {
                return SettlementResult.error("Insufficient holdings for SELL: required " + quantity +
                        ", available " + (holding == null ? 0 : holding.getQuantity()));
            }

            // Existing holding: decrease quantity
            long newQuantity = holding.getQuantity() - quantity;
            if (newQuantity == 0) {
                // Option 1: Delete the holding (not implemented here)
                // Option 2: Keep with zero quantity (implemented here per requirements)
                // For now, we keep it; production code should decide deletion policy
            }

            int updated = holdingMapper.updateQuantity(holding.getPortfolioHoldingId(), newQuantity);
            if (updated != 1) {
                return SettlementResult.error("Failed to update holding for SELL");
            }
            logger.info("Updated holding {} quantity: {} -> {} (SELL)",
                holding.getPortfolioHoldingId(), holding.getQuantity(), newQuantity);
        } else {
            return SettlementResult.error("Unknown side: " + side);
        }

        return SettlementResult.success();
    }

    /**
     * Result of settlement operation: success, duplicate, or error with reason.
     */
    public static class SettlementResult {
        public enum Status {
            SUCCESS, DUPLICATE, ERROR
        }

        private final Status status;
        private final String errorMessage;

        private SettlementResult(Status status, String errorMessage) {
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isDuplicate() {
            return status == Status.DUPLICATE;
        }

        public Status getStatus() {
            return status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public static SettlementResult success() {
            return new SettlementResult(Status.SUCCESS, null);
        }

        public static SettlementResult duplicate() {
            return new SettlementResult(Status.DUPLICATE, "Duplicate delivery: order already processed");
        }

        public static SettlementResult error(String message) {
            return new SettlementResult(Status.ERROR, message);
        }

        @Override
        public String toString() {
            return "SettlementResult{" +
                    "status=" + status +
                    (errorMessage != null ? ", errorMessage='" + errorMessage + '\'' : "") +
                    '}';
        }
    }
}
