package com.team5.strategies.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.domain.exception.OrderConflictException;
import com.team5.service.AccountService;
import com.team5.strategies.config.StrategyProperties;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.domain.StrategyType;
import com.team5.strategies.dto.CreateStrategyRequest;
import com.team5.strategies.dto.StrategyExecutionResponse;
import com.team5.strategies.dto.StrategyListResponse;
import com.team5.strategies.dto.StrategyResponse;
import com.team5.strategies.exception.InvalidStrategyException;
import com.team5.strategies.exception.StrategyNotFoundException;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyReadMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's side of the module: configure, list, switch on and off, delete, and see what each
 * strategy did. Every route addresses an account, and the token's account must be it; a strategy
 * belonging to another account is ACC-403 and nothing is read or written.
 *
 * Disabling is the control a customer reaches for when something is wrong, so it is immediate: the
 * UPDATE takes the same row lock every order decision is taken under. If an order for the strategy is
 * being decided at that moment, the disable waits for that one decision to finish (at most the Trade
 * API's read timeout) and returns once it is committed. No order is started for the strategy after.
 */
@Service
public class StrategyService {

    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);

    static final int EXECUTION_HISTORY_LIMIT = 50;
    static final String DISABLED_BY_CUSTOMER = "Disabled by the customer";
    private static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 3;

    private final StrategyMapper strategyMapper;
    private final StrategyExecutionMapper executionMapper;
    private final StrategyReadMapper readMapper;
    private final AccountService accountService;
    private final AccountAccessAuthorizer authorizer;
    private final int maxPerAccount;
    private final Clock clock;

    public StrategyService(
        StrategyMapper strategyMapper,
        StrategyExecutionMapper executionMapper,
        StrategyReadMapper readMapper,
        AccountService accountService,
        AccountAccessAuthorizer authorizer,
        StrategyProperties properties,
        Clock clock
    ) {
        this.strategyMapper = strategyMapper;
        this.executionMapper = executionMapper;
        this.readMapper = readMapper;
        this.accountService = accountService;
        this.authorizer = authorizer;
        this.maxPerAccount = properties.maxPerAccount();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StrategyListResponse list(long authenticatedAccountId, long accountId) {
        assertSameAccount(authenticatedAccountId, accountId);
        List<StrategyResponse> strategies = strategyMapper.findByAccount(accountId).stream()
            .map(StrategyResponse::from)
            .toList();
        return new StrategyListResponse(maxPerAccount, strategies);
    }

    @Transactional
    public StrategyResponse create(long authenticatedAccountId, long accountId, CreateStrategyRequest request) {
        assertSameAccount(authenticatedAccountId, accountId);
        // ACC-404 when the account does not exist, ACC-403 when it is not ACTIVE
        accountService.getAccount(authenticatedAccountId, accountId);
        validate(request);

        String symbol = readMapper.findTradableSymbol(request.getSymbol().trim());
        if (symbol == null) {
            throw new InstrumentNotFoundException();
        }

        // held until commit: a second create for this account waits here and then counts this one
        strategyMapper.lockAccountStrategies(accountId);
        if (strategyMapper.countByAccount(accountId) >= maxPerAccount) {
            throw new InvalidStrategyException("Strategy limit reached");
        }

        StrategyRow row = new StrategyRow();
        row.setAccountId(accountId);
        row.setName(request.getName().trim());
        row.setSymbol(symbol);
        row.setStrategyType(request.getType());
        row.setShortWindow(request.getShortWindow());
        row.setLongWindow(request.getLongWindow());
        row.setBandWindow(request.getBandWindow());
        row.setBandWidth(request.getBandWidth());
        row.setQuantity(request.getQuantity());
        row.setMaxSpend(request.getMaxSpend());
        row.setMaxPosition(request.getMaxPosition());
        row.setMaxConsecutiveFailures(request.getMaxConsecutiveFailures() == null
            ? DEFAULT_MAX_CONSECUTIVE_FAILURES : request.getMaxConsecutiveFailures());
        row.setStatus(StrategyStatus.ENABLED);
        row.setCreatedAt(OffsetDateTime.now(clock));
        if (strategyMapper.insert(row) != 1) {
            throw new OrderConflictException("Strategy insert did not affect exactly one row");
        }
        log.info("Account {} created strategy {} ({} on {}, {} per order, max spend {}, max position {})",
            accountId, row.getStrategyId(), row.getStrategyType(), symbol, row.getQuantity(), row.getMaxSpend(),
            row.getMaxPosition());
        return StrategyResponse.from(strategyMapper.findForAccount(row.getStrategyId(), accountId));
    }

    @Transactional(readOnly = true)
    public StrategyResponse get(long authenticatedAccountId, long accountId, long strategyId) {
        return StrategyResponse.from(owned(authenticatedAccountId, accountId, strategyId));
    }

    @Transactional
    public StrategyResponse disable(long authenticatedAccountId, long accountId, long strategyId) {
        owned(authenticatedAccountId, accountId, strategyId);
        // waits for the strategy's row lock if an order decision holds it, then commits at return
        strategyMapper.updateStatus(strategyId, accountId, StrategyStatus.DISABLED, DISABLED_BY_CUSTOMER, false,
            OffsetDateTime.now(clock));
        log.info("Account {} disabled strategy {}", accountId, strategyId);
        return StrategyResponse.from(strategyMapper.findForAccount(strategyId, accountId));
    }

    @Transactional
    public StrategyResponse enable(long authenticatedAccountId, long accountId, long strategyId) {
        owned(authenticatedAccountId, accountId, strategyId);
        accountService.getAccount(authenticatedAccountId, accountId);
        // a fresh start: re-enabling a HALTED strategy clears the failure run that halted it
        strategyMapper.updateStatus(strategyId, accountId, StrategyStatus.ENABLED, null, true, OffsetDateTime.now(clock));
        log.info("Account {} enabled strategy {}", accountId, strategyId);
        return StrategyResponse.from(strategyMapper.findForAccount(strategyId, accountId));
    }

    @Transactional
    public void delete(long authenticatedAccountId, long accountId, long strategyId) {
        owned(authenticatedAccountId, accountId, strategyId);
        // like disable, waits for a decision in progress; its executions go with it (ON DELETE CASCADE)
        strategyMapper.delete(strategyId, accountId);
        log.info("Account {} deleted strategy {}", accountId, strategyId);
    }

    @Transactional(readOnly = true)
    public List<StrategyExecutionResponse> executions(long authenticatedAccountId, long accountId, long strategyId) {
        owned(authenticatedAccountId, accountId, strategyId);
        return executionMapper.findByStrategy(strategyId, EXECUTION_HISTORY_LIMIT).stream()
            .map(StrategyExecutionResponse::from)
            .toList();
    }

    private StrategyRow owned(long authenticatedAccountId, long accountId, long strategyId) {
        assertSameAccount(authenticatedAccountId, accountId);
        Long owner = strategyMapper.findOwner(strategyId);
        if (owner == null) {
            throw new StrategyNotFoundException();
        }
        assertSameAccount(authenticatedAccountId, owner);
        StrategyRow row = strategyMapper.findForAccount(strategyId, accountId);
        if (row == null) {
            throw new StrategyNotFoundException();
        }
        return row;
    }

    private void assertSameAccount(long authenticatedAccountId, Long addressedAccountId) {
        try {
            authorizer.assertAuthorised(authenticatedAccountId, addressedAccountId);
        } catch (AccountAccessDeniedException e) {
            log.warn("Strategy access denied: token account {} addressed account {}",
                authenticatedAccountId, addressedAccountId);
            throw e;
        }
    }

    // The parameters that go with each type, and limits that could never let an order through.
    static void validate(CreateStrategyRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new InvalidStrategyException("A strategy needs a name");
        }
        StrategyType type = request.getType();
        if (type == StrategyType.MA_CROSSOVER) {
            if (request.getShortWindow() == null || request.getLongWindow() == null) {
                throw new InvalidStrategyException("A moving-average crossover needs shortWindow and longWindow");
            }
            if (request.getShortWindow() >= request.getLongWindow()) {
                throw new InvalidStrategyException("shortWindow must be smaller than longWindow");
            }
            if (request.getBandWindow() != null || request.getBandWidth() != null) {
                throw new InvalidStrategyException("bandWindow and bandWidth apply to Bollinger bands only");
            }
        } else if (type == StrategyType.BOLLINGER_BANDS) {
            if (request.getBandWindow() == null || request.getBandWidth() == null) {
                throw new InvalidStrategyException("Bollinger bands need bandWindow and bandWidth");
            }
            if (request.getShortWindow() != null || request.getLongWindow() != null) {
                throw new InvalidStrategyException("shortWindow and longWindow apply to moving-average crossovers only");
            }
        } else {
            throw new InvalidStrategyException("Unknown strategy type");
        }
        if (request.getMaxPosition() < request.getQuantity()) {
            throw new InvalidStrategyException("maxPosition must be at least quantity, or no BUY could ever be placed");
        }
        if (request.getMaxSpend().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidStrategyException("maxSpend must be more than zero");
        }
    }
}
