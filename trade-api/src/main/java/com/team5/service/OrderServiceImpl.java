package com.team5.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.exception.AccountNotActiveException;
import com.team5.exception.AccountNotFoundException;
import com.team5.exception.InstrumentNotFoundException;
import com.team5.exception.InsufficientFundsException;
import com.team5.exception.InsufficientHoldingsException;
import com.team5.exception.OrderConflictException;
import com.team5.exception.OrderNotFoundException;
import com.team5.exception.DuplicateOrderException;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.HoldingMapper;
import com.team5.mapper.OrderMapper;
import com.team5.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String NEW_STATUS = "NEW";
    private static final String CANCELLED_STATUS = "CANCELLED";

    private final AccountQueryMapper accountQueryMapper;
    private final OrderMapper orderMapper;
    private final HoldingMapper holdingMapper;
    private final TransactionMapper transactionMapper;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public OrderServiceImpl(
        AccountQueryMapper accountQueryMapper,
        OrderMapper orderMapper,
        HoldingMapper holdingMapper,
        TransactionMapper transactionMapper,
        AccountAccessAuthorizer accountAccessAuthorizer
    ) {
        this.accountQueryMapper = accountQueryMapper;
        this.orderMapper = orderMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(long authenticatedAccountId, PlaceOrderRequest request) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, request.getAccountId());

        Map<String, Object> account = accountQueryMapper.findAccountForOrder(request.getAccountId());
        if (account == null) {
            throw new AccountNotFoundException();
        }
        if (!"ACTIVE".equals(asString(account, "status"))) {
            throw new AccountNotActiveException();
        }

        Map<String, Object> instrument = orderMapper.findInstrumentByTicker(request.getSymbol());
        if (instrument == null || !Boolean.TRUE.equals(asBoolean(instrument, "tradable"))) {
            throw new InstrumentNotFoundException();
        }

        Long portfolioId = orderMapper.findPortfolioIdByAccountId(request.getAccountId());
        if (portfolioId == null) {
            throw new AccountNotFoundException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal quantity = BigDecimal.valueOf(request.getQuantity().longValue());
        BigDecimal amount = request.getPrice().multiply(quantity).setScale(4, RoundingMode.HALF_UP);
        BigDecimal balanceBefore = asBigDecimal(account, "balance").setScale(4, RoundingMode.HALF_UP);
        BigDecimal balanceAfter = calculateBalanceAfter(request, balanceBefore, amount);

        int updatedAccounts = accountQueryMapper.updateBalanceWithVersion(
            request.getAccountId(),
            balanceAfter.setScale(2, RoundingMode.HALF_UP),
            asLong(account, "version"),
            now
        );
        if (updatedAccounts != 1) {
            throw new OrderConflictException("Concurrent account update detected");
        }

        applyHoldingChange(
            portfolioId,
            asLong(instrument, "instrument_id"),
            request.getQuantity().longValue(),
            request.getPrice(),
            request.getSide().name(),
            now
        );

        UUID orderUuid = UUID.randomUUID();
        String externalOrderId = "ORD-" + orderUuid;
        Long persistentOrderId = toPositiveLong(orderUuid);

        try {
            int insertedOrders = orderMapper.insertOrder(
                persistentOrderId,
                portfolioId,
                asLong(instrument, "instrument_id"),
                request.getSide().name(),
                request.getQuantity().longValue(),
                request.getPrice().setScale(4, RoundingMode.HALF_UP),
                NEW_STATUS,
                request.getIdempotencyKey(),
                now,
                now
            );
            if (insertedOrders != 1) {
                throw new OrderConflictException("Order insert did not affect exactly one row");
            }
        } catch (DataAccessException exception) {
            throw new DuplicateOrderException();
        }

        int insertedTransactions = transactionMapper.insertTransaction(
            toPositiveLong(UUID.randomUUID()),
            request.getAccountId(),
            persistentOrderId,
            asLong(instrument, "instrument_id"),
            request.getSide().name(),
            request.getQuantity().longValue(),
            request.getPrice().setScale(4, RoundingMode.HALF_UP),
            amount,
            asString(account, "base_currency"),
            balanceBefore,
            balanceAfter,
            "txn-" + externalOrderId,
            now
        );
        if (insertedTransactions != 1) {
            throw new OrderConflictException("Transaction insert did not affect exactly one row");
        }

        return orderMapper.findOrderById(persistentOrderId, externalOrderId);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(long authenticatedAccountId, UUID orderId) {
        String externalOrderId = "ORD-" + orderId;
        Long persistentOrderId = toPositiveLong(orderId);

        Long ownerAccountId = orderMapper.findAccountIdByOrderId(persistentOrderId);
        if (ownerAccountId == null) {
            throw new OrderNotFoundException();
        }
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, ownerAccountId);

        // Ownership is repeated in the UPDATE so the transition can never touch another account's order.
        int updatedRows = orderMapper.updateOrderStatusConditionally(
            persistentOrderId,
            authenticatedAccountId,
            NEW_STATUS,
            CANCELLED_STATUS,
            OffsetDateTime.now()
        );

        if (updatedRows != 1) {
            throw new OrderConflictException("Invalid order state transition");
        }

        return orderMapper.findOrderById(persistentOrderId, externalOrderId);
    }

    private BigDecimal calculateBalanceAfter(PlaceOrderRequest request, BigDecimal balanceBefore, BigDecimal amount) {
        if ("BUY".equals(request.getSide().name())) {
            if (balanceBefore.compareTo(amount) < 0) {
                throw new InsufficientFundsException();
            }
            return balanceBefore.subtract(amount).setScale(4, RoundingMode.HALF_UP);
        }
        return balanceBefore.add(amount).setScale(4, RoundingMode.HALF_UP);
    }

    private void applyHoldingChange(
        Long portfolioId,
        Long instrumentId,
        Long orderQuantity,
        BigDecimal price,
        String side,
        OffsetDateTime updatedAt
    ) {
        Map<String, Object> holding = holdingMapper.findHolding(portfolioId, instrumentId);
        if ("BUY".equals(side)) {
            applyBuyHolding(portfolioId, instrumentId, orderQuantity, price, holding, updatedAt);
            return;
        }

        if (holding == null || asLong(holding, "quantity") < orderQuantity) {
            throw new InsufficientHoldingsException();
        }

        long remainingQuantity = asLong(holding, "quantity") - orderQuantity;
        if (remainingQuantity == 0L) {
            int deletedRows = holdingMapper.deleteHolding(asLong(holding, "portfolio_holding_id"));
            if (deletedRows != 1) {
                throw new OrderConflictException("Holding delete did not affect exactly one row");
            }
            return;
        }

        int updatedRows = holdingMapper.updateHolding(
            asLong(holding, "portfolio_holding_id"),
            remainingQuantity,
            asBigDecimal(holding, "avg_cost"),
            updatedAt
        );
        if (updatedRows != 1) {
            throw new OrderConflictException("Holding update did not affect exactly one row");
        }
    }

    private void applyBuyHolding(
        Long portfolioId,
        Long instrumentId,
        Long orderQuantity,
        BigDecimal price,
        Map<String, Object> currentHolding,
        OffsetDateTime updatedAt
    ) {
        BigDecimal normalizedPrice = price.setScale(4, RoundingMode.HALF_UP);
        if (currentHolding == null) {
            int insertedRows = holdingMapper.insertHolding(
                toPositiveLong(UUID.randomUUID()),
                portfolioId,
                instrumentId,
                orderQuantity,
                normalizedPrice,
                updatedAt,
                updatedAt
            );
            if (insertedRows != 1) {
                throw new OrderConflictException("Holding insert did not affect exactly one row");
            }
            return;
        }

        long currentQuantity = asLong(currentHolding, "quantity");
        long newQuantity = currentQuantity + orderQuantity;
        BigDecimal oldCost = asBigDecimal(currentHolding, "avg_cost").multiply(BigDecimal.valueOf(currentQuantity));
        BigDecimal newCost = normalizedPrice.multiply(BigDecimal.valueOf(orderQuantity));
        BigDecimal newAverageCost = oldCost.add(newCost)
            .divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP);

        int updatedRows = holdingMapper.updateHolding(
            asLong(currentHolding, "portfolio_holding_id"),
            newQuantity,
            newAverageCost,
            updatedAt
        );
        if (updatedRows != 1) {
            throw new OrderConflictException("Holding update did not affect exactly one row");
        }
    }

    private Long toPositiveLong(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return mixed == Long.MIN_VALUE ? 0L : Math.abs(mixed);
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Long asLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new OrderConflictException("Unexpected numeric mapping for " + key);
    }

    private BigDecimal asBigDecimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        throw new OrderConflictException("Unexpected decimal mapping for " + key);
    }

    private Boolean asBoolean(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
