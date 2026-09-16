package com.team5.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.exception.AccountNotActiveException;
import com.team5.exception.AccountNotFoundException;
import com.team5.exception.InstrumentNotFoundException;
import com.team5.exception.OrderConflictException;
import com.team5.exception.OrderNotFoundException;
import com.team5.exception.DuplicateOrderException;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.OrderMapper;
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
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    // last placed order details, used by the controller to publish after commit
    private UUID lastOrderUuid;
    private OffsetDateTime lastOrderCreatedOn;

    public OrderServiceImpl(
        AccountQueryMapper accountQueryMapper,
        OrderMapper orderMapper,
        AccountAccessAuthorizer accountAccessAuthorizer
    ) {
        this.accountQueryMapper = accountQueryMapper;
        this.orderMapper = orderMapper;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(long authenticatedAccountId, PlaceOrderRequest request) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, request.getAccountId());

        // validate account exists and is active
        Map<String, Object> account = accountQueryMapper.findAccountForOrder(request.getAccountId());
        if (account == null) {
            throw new AccountNotFoundException();
        }
        if (!"ACTIVE".equals(asString(account, "status"))) {
            throw new AccountNotActiveException();
        }

        // validate instrument exists and is tradable
        Map<String, Object> instrument = orderMapper.findInstrumentByTicker(request.getSymbol());
        if (instrument == null || !Boolean.TRUE.equals(asBoolean(instrument, "tradable"))) {
            throw new InstrumentNotFoundException();
        }

        Long portfolioId = orderMapper.findPortfolioIdByAccountId(request.getAccountId());
        if (portfolioId == null) {
            throw new AccountNotFoundException();
        }

        // insert the order as NEW, no fill — pricing is the executor's job
        OffsetDateTime now = OffsetDateTime.now();
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

        // store for the controller to use when publishing after commit
        this.lastOrderUuid = orderUuid;
        this.lastOrderCreatedOn = now;

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

    public UUID getLastOrderUuid() {
        return lastOrderUuid;
    }

    public OffsetDateTime getLastOrderCreatedOn() {
        return lastOrderCreatedOn;
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

    private Boolean asBoolean(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}