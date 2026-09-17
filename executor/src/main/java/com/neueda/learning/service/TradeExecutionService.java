package com.neueda.learning.service;

import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.domain.ExecutionDecision;
import com.neueda.learning.domain.ExecutionResult;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import com.neueda.learning.mapper.AccountMapper;
import com.neueda.learning.mapper.InstrumentMapper;
import com.neueda.learning.mapper.OrderMapper;
import com.neueda.learning.mapper.PortfolioMapper;
import com.neueda.learning.model.AccountRecord;
import com.neueda.learning.model.InstrumentRecord;
import com.neueda.learning.model.OrderRecord;
import com.neueda.learning.model.PortfolioRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * Orchestrates trade execution flow:
 * 1. Load order and supporting entities from DB
 * 2. Validate execution-time conditions
 * 3. Obtain quote
 * 4. Apply pure decision function
 * 5. Return execution result
 *
 * Does NOT modify database or resolve orders; settlement happens in next story.
 */
@Service
public class TradeExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(TradeExecutionService.class);

    private final OrderMapper orderMapper;
    private final PortfolioMapper portfolioMapper;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final FauxnanceQuoteClient quoteClient;

    public TradeExecutionService(OrderMapper orderMapper,
                                 PortfolioMapper portfolioMapper,
                                 AccountMapper accountMapper,
                                 InstrumentMapper instrumentMapper,
                                 FauxnanceQuoteClient quoteClient) {
        this.orderMapper = orderMapper;
        this.portfolioMapper = portfolioMapper;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.quoteClient = quoteClient;
    }

    /**
     * Execute a trade order.
     *
     * @param orderId the order ID to execute
     * @return ExecutionResult with FILLED/REJECTED status
     */
    public ExecutionResult executeOrder(Long orderId) {
        logger.info("Starting execution for order {}", orderId);

        try {
            // 1. Load order
            OrderRecord orderRecord = orderMapper.findById(orderId);
            if (orderRecord == null) {
                logger.warn("Order {} not found in database", orderId);
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Order not found");
            }

            logger.debug("Loaded order: {}", orderRecord);

            // 2. Check if order is NEW (skip if already terminal)
            if (!"NEW".equalsIgnoreCase(orderRecord.getStatus())) {
                logger.info("Order {} has terminal status: {}, skipping execution", orderId, orderRecord.getStatus());
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Order is already " + orderRecord.getStatus());
            }

            // 3. Load portfolio
            PortfolioRecord portfolioRecord = portfolioMapper.findById(orderRecord.getPortfolioId());
            if (portfolioRecord == null) {
                logger.warn("Portfolio {} not found for order {}", orderRecord.getPortfolioId(), orderId);
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Portfolio not found");
            }

            logger.debug("Loaded portfolio: {}", portfolioRecord);

            // 4. Load account
            AccountRecord accountRecord = accountMapper.findById(portfolioRecord.getAccountId());
            if (accountRecord == null) {
                logger.warn("Account {} not found for portfolio {}", portfolioRecord.getAccountId(), orderId);
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Account not found");
            }

            logger.debug("Loaded account: {}", accountRecord);

            // 5. Load instrument
            InstrumentRecord instrumentRecord = instrumentMapper.findById(orderRecord.getInstrumentId());
            if (instrumentRecord == null) {
                logger.warn("Instrument {} not found for order {}", orderRecord.getInstrumentId(), orderId);
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Instrument not found");
            }

            logger.debug("Loaded instrument: {}", instrumentRecord);

            // 6. Validate execution-time conditions
            ExecutionResult checkResult = validateExecutionTimeConditions(
                    orderId, accountRecord, instrumentRecord, orderRecord);
            if (checkResult != null) {
                return checkResult; // Validation failed
            }

            // 7. Get quote from Fauxnance
            Quote quote;
            try {
                quote = quoteClient.getQuote(instrumentRecord.getTicker());
                logger.debug("Quote obtained: {}", quote);
            } catch (QuoteUnavailableException e) {
                logger.error("Quote unavailable for {} after retries: {}", instrumentRecord.getTicker(), e.getMessage());
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Quote unavailable: " + e.getMessage());
            }

            // 8. Apply pure decision function
            ExecutionDecision decision = ExecutionDecision.decide(
                    orderRecord.getSide(),
                    orderRecord.getPrice(),
                    quote
            );

            logger.info("Execution decision for order {}: {}", orderId, decision);

            // 9. Convert decision to result
            return new ExecutionResult(
                    orderId,
                    decision.getStatus(),
                    decision.getExecutionPrice(),
                    decision.getReason()
            );

        } catch (Exception e) {
            logger.error("Unexpected error executing order {}: {}", orderId, e.getMessage(), e);
            return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                    "Execution error: " + e.getMessage());
        }
    }

    /**
     * Validate execution-time conditions per Sprint 5 rules.
     * Returns null if all checks pass, otherwise returns a rejection result.
     */
    private ExecutionResult validateExecutionTimeConditions(Long orderId,
                                                           AccountRecord account,
                                                           InstrumentRecord instrument,
                                                           OrderRecord order) {

        // Check account is ACTIVE
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            logger.warn("Account {} is not ACTIVE (status: {}), rejecting order {}", 
                    account.getAccId(), account.getStatus(), orderId);
            return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                    "Account is not active: " + account.getStatus());
        }

        // Check instrument is tradable
        if (!Boolean.TRUE.equals(instrument.getTradable())) {
            logger.warn("Instrument {} is not tradable, rejecting order {}", 
                    instrument.getInstrumentId(), orderId);
            return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                    "Instrument is not tradable");
        }

        // Check BUY affordability: quantity × executionPrice must be affordable
        // For BUY, we need to check if the account can afford the order at the LIMIT price (worst case)
        // The actual execution price may be better (lower ask), but we must validate against limit
        if ("BUY".equalsIgnoreCase(order.getSide())) {
            BigDecimal cost = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            if (account.getBalance() == null || account.getBalance().compareTo(cost) < 0) {
                logger.warn("Account {} cannot afford BUY order {}: {} < required {}", 
                        account.getAccId(), orderId, account.getBalance(), cost);
                return new ExecutionResult(orderId, com.neueda.learning.domain.enums.ExecutionStatus.REJECTED, null,
                        "Insufficient funds: " + account.getBalance() + " < " + cost);
            }
        }

        // For SELL: validation would check holdings, but that's in portfolio_holdings table
        // which is not the focus of this executor story
        // The ordering system (Sprint 5) would have already validated holdings at order placement

        return null; // All checks passed
    }
}
