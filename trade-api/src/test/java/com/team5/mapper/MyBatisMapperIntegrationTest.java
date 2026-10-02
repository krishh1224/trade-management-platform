package com.team5.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.TransactionType;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.CashMovementConflictException;
import com.team5.domain.exception.DuplicateOrderException;
import com.team5.domain.exception.OrderConflictException;
import com.team5.domain.exception.OrderNotFoundException;
import com.team5.domain.exception.WithdrawalExceedsAvailableException;
import com.team5.dto.request.CashMovementRequest;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.CashTransactionResponse;
import com.team5.dto.response.OrderResponse;
import com.team5.service.AccountService;
import com.team5.service.OrderService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.hikari.schema=mapper_test"
})
class MyBatisMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS mapper_test");
        jdbcTemplate.execute("DROP TABLE IF EXISTS transactions CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS orders CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS portfolio_holdings CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS portfolio CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS instruments CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS accounts CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS clients CASCADE");

        jdbcTemplate.execute("""
            CREATE TABLE clients (
              id BIGINT PRIMARY KEY,
                            acc_id BIGINT NOT NULL UNIQUE,
              name VARCHAR(100) NOT NULL,
              email VARCHAR(255) NOT NULL UNIQUE,
              phone VARCHAR(20) NOT NULL UNIQUE,
              dob DATE NOT NULL,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE accounts (
              acc_id BIGINT PRIMARY KEY,
              account_number VARCHAR(10) NOT NULL UNIQUE,
              risk_profile VARCHAR(20) NOT NULL,
              base_currency VARCHAR(3) NOT NULL,
              balance NUMERIC(19,2) NOT NULL,
              status VARCHAR(20) NOT NULL,
              version BIGINT NOT NULL,
              created_at TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP NOT NULL
            )
            """);

                jdbcTemplate.execute("""
                        ALTER TABLE clients
                        ADD CONSTRAINT fk_clients_account FOREIGN KEY (acc_id) REFERENCES accounts(acc_id)
                        """);

        jdbcTemplate.execute("""
            CREATE TABLE portfolio (
              portfolio_id BIGINT PRIMARY KEY,
              name VARCHAR(100) NOT NULL,
              account_id BIGINT NOT NULL UNIQUE,
              status VARCHAR(30) NOT NULL,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              CONSTRAINT fk_portfolio_account FOREIGN KEY (account_id) REFERENCES accounts(acc_id)
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE instruments (
              instrument_id BIGINT PRIMARY KEY,
              name VARCHAR(150) NOT NULL,
              ticker VARCHAR(20) NOT NULL UNIQUE,
              asset_class VARCHAR(50) NOT NULL,
              currency VARCHAR(3) NOT NULL,
              exchange VARCHAR(10) NOT NULL,
              tradable BOOLEAN NOT NULL
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE portfolio_holdings (
              portfolio_holding_id BIGINT PRIMARY KEY,
              portfolio_id BIGINT NOT NULL,
              instrument_id BIGINT NOT NULL,
              quantity BIGINT NOT NULL,
              avg_cost NUMERIC(19,4) NOT NULL,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              CONSTRAINT fk_holdings_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(portfolio_id),
              CONSTRAINT fk_holdings_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id),
              CONSTRAINT uq_holdings_portfolio_instrument UNIQUE (portfolio_id, instrument_id)
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE orders (
              order_id BIGINT PRIMARY KEY,
              external_order_id VARCHAR(50),
              portfolio_id BIGINT NOT NULL,
              instrument_id BIGINT NOT NULL,
              side VARCHAR(10) NOT NULL,
              quantity BIGINT NOT NULL CHECK (quantity > 0),
              price NUMERIC(19,4) NOT NULL CHECK (price > 0),
              status VARCHAR(30) NOT NULL,
              idempotency_key VARCHAR(100) NOT NULL UNIQUE,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              expires_at TIMESTAMP,
              CONSTRAINT fk_orders_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(portfolio_id),
              CONSTRAINT fk_orders_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE transactions (
              transaction_id BIGINT PRIMARY KEY,
              account_id BIGINT NOT NULL,
              order_id BIGINT,
              instrument_id BIGINT,
              transaction_type VARCHAR(20) NOT NULL,
              quantity BIGINT,
              price NUMERIC(19,4),
              amount NUMERIC(19,4) NOT NULL,
              currency VARCHAR(3) NOT NULL,
              balance_before NUMERIC(19,4) NOT NULL,
              balance_after NUMERIC(19,4) NOT NULL,
              idempotency_key VARCHAR(100) NOT NULL UNIQUE,
              created_at TIMESTAMP NOT NULL,
              CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts(acc_id),
              CONSTRAINT fk_transactions_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
              CONSTRAINT fk_transactions_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
            )
            """);

        seedBaseData();
    }

    // Renamed from placeOrderCommitsOrderBalanceHoldingsAndTransaction, and the
    // balance/holdings/transaction assertions it used to carry were dropped. Trade REST
    // API no longer performs a synchronous fill — pricing and cash/position movement are
    // the executor's job (see "Trade REST API Publishes ORDER_PLACED and Answers NEW").
    // Those assertions will be re-covered by the executor's own test suite in Story 5.
    @Test
    void placeOrderCommitsOrderAtNewStatus() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(101L);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("1000.00"));
        request.setIdempotencyKey("idem-commit-1");

        OrderResponse response = orderService.placeOrder(101L, request);

        assertNotNull(response);
        assertTrue(response.orderId().startsWith("ORD-"));
        assertEquals("NEW", response.status().name());

        Integer orderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE idempotency_key = 'idem-commit-1'",
            Integer.class
        );
        assertEquals(1, orderCount);
    }

    @Test
    void failedOrderRollsBackWithoutPartialWrites() {
        jdbcTemplate.update(
            """
            INSERT INTO orders (order_id, portfolio_id, instrument_id, side, quantity, price, status, idempotency_key, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            900001L,
            201L,
            301L,
            "NEW",
            1L,
            new BigDecimal("100.0000"),
            "NEW",
            "dup-key",
            OffsetDateTime.parse("2026-09-10T10:00:00Z"),
            OffsetDateTime.parse("2026-09-10T10:00:00Z")
        );

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(101L);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("1000.00"));
        request.setIdempotencyKey("dup-key");

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(101L, request));

        BigDecimal balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE acc_id = 101",
            BigDecimal.class
        );
        assertEquals(new BigDecimal("50000.00"), balance);

        Long version = jdbcTemplate.queryForObject(
            "SELECT version FROM accounts WHERE acc_id = 101",
            Long.class
        );
        assertEquals(1L, version);

        Long quantity = jdbcTemplate.queryForObject(
            "SELECT quantity FROM portfolio_holdings WHERE portfolio_id = 201 AND instrument_id = 301",
            Long.class
        );
        assertEquals(100L, quantity);

        Integer transactionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE idempotency_key LIKE 'txn-ORD-%'",
            Integer.class
        );
        assertEquals(0, transactionCount);
    }

    @Test
    void cancellingFilledOrderReturnsOrd409() {
        UUID orderUuid = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        Long persistentOrderId = toPositiveLong(orderUuid);

        jdbcTemplate.update(
            """
            INSERT INTO orders (order_id, portfolio_id, instrument_id, side, quantity, price, status, idempotency_key, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            persistentOrderId,
            201L,
            301L,
            "BUY",
            10L,
            new BigDecimal("1200.0000"),
            "FILLED",
            "filled-cancel-key",
            OffsetDateTime.parse("2026-09-10T10:00:00Z"),
            OffsetDateTime.parse("2026-09-10T10:00:00Z")
        );

        OrderConflictException exception = assertThrows(
            OrderConflictException.class,
            () -> orderService.cancelOrder(101L, orderUuid)
        );

        assertEquals("ORD-409", exception.getCode());
    }

    @Test
    void cancellingAnotherAccountsOrderIsRejectedAndLeavesOrderUntouched() {
        UUID orderUuid = UUID.fromString("0d6a3f5e-1b2c-4d3e-8f90-a1b2c3d4e5f6");
        Long persistentOrderId = toPositiveLong(orderUuid);
        insertOrder(persistentOrderId, "NEW", "other-account-cancel-key");

        assertThrows(AccountAccessDeniedException.class, () -> orderService.cancelOrder(999L, orderUuid));

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE order_id = ?", String.class, persistentOrderId);
        assertEquals("NEW", status);
    }

    @Test
    void cancellingUnknownOrderReturnsNotFound() {
        assertThrows(
            OrderNotFoundException.class,
            () -> orderService.cancelOrder(101L, UUID.fromString("11111111-2222-3333-4444-555555555555"))
        );
    }

    @Test
    void cancellingOwnNewOrderMarksItCancelled() {
        UUID orderUuid = UUID.fromString("7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d");
        Long persistentOrderId = toPositiveLong(orderUuid);
        insertOrder(persistentOrderId, "NEW", "own-cancel-key");

        OrderResponse response = orderService.cancelOrder(101L, orderUuid);

        assertEquals("CANCELLED", response.status().name());
    }

    @Test
    void depositUpdatesBalanceAndWritesOneLedgerRow() {
        CashTransactionResponse response = accountService.deposit(101L, 101L, cashRequest("2500.50", "cash-deposit-1"));

        assertEquals(new BigDecimal("52500.50"), response.balanceAfter());
        assertEquals(new BigDecimal("52500.50"), balanceOf101());
        assertEquals(2L, jdbcTemplate.queryForObject("SELECT version FROM accounts WHERE acc_id = 101", Long.class));

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT transaction_type, amount, balance_before, balance_after, order_id FROM transactions WHERE idempotency_key = 'cash-deposit-1'");
        assertEquals("DEPOSIT", row.get("transaction_type"));
        assertEquals(0, new BigDecimal("2500.50").compareTo((BigDecimal) row.get("amount")));
        assertEquals(0, new BigDecimal("50000.00").compareTo((BigDecimal) row.get("balance_before")));
        assertEquals(0, new BigDecimal("52500.50").compareTo((BigDecimal) row.get("balance_after")));
        assertNull(row.get("order_id"));
    }

    @Test
    void withdrawalCannotTouchCashHeldForNewBuyOrders() {
        // NEW buy of 10 x 1200 holds 12000, so only 38000 can be withdrawn
        insertOrder(910001L, "NEW", "held-buy-key");

        BalanceResponse balance = accountService.getBalance(101L, 101L);
        assertEquals(0, new BigDecimal("12000.00").compareTo(balance.heldForOrders()));
        assertEquals(0, new BigDecimal("38000.00").compareTo(balance.availableToWithdraw()));

        assertThrows(
            WithdrawalExceedsAvailableException.class,
            () -> accountService.withdraw(101L, 101L, cashRequest("38000.01", "cash-withdraw-too-much"))
        );
        assertEquals(new BigDecimal("50000.00"), balanceOf101());

        CashTransactionResponse response = accountService.withdraw(101L, 101L, cashRequest("38000.00", "cash-withdraw-max"));
        assertEquals(new BigDecimal("12000.00"), response.balanceAfter());
        assertEquals(new BigDecimal("12000.00"), balanceOf101());
    }

    @Test
    void reusedCashIdempotencyKeyMovesNoMoney() {
        accountService.deposit(101L, 101L, cashRequest("100.00", "cash-reused-key"));

        assertThrows(
            CashMovementConflictException.class,
            () -> accountService.deposit(101L, 101L, cashRequest("100.00", "cash-reused-key"))
        );

        assertEquals(new BigDecimal("50100.00"), balanceOf101());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE idempotency_key = 'cash-reused-key'", Integer.class));
    }

    @Test
    void cashHistoryIsNewestFirstAndExcludesTrades() {
        accountService.deposit(101L, 101L, cashRequest("1000.00", "cash-history-1"));
        accountService.withdraw(101L, 101L, cashRequest("400.00", "cash-history-2"));
        jdbcTemplate.update(
            """
            INSERT INTO transactions (transaction_id, account_id, transaction_type, amount, currency, balance_before, balance_after, idempotency_key, created_at)
            VALUES (777001, 101, 'BUY', 10, 'INR', 50600, 50590, 'trade-settlement-1', ?)
            """,
            OffsetDateTime.now().plusMinutes(1)
        );

        List<CashTransactionResponse> history = accountService.getCashTransactions(101L, 101L, 20);

        assertEquals(2, history.size());
        assertEquals(TransactionType.WITHDRAWAL, history.get(0).type());
        assertEquals(TransactionType.DEPOSIT, history.get(1).type());
        assertEquals(new BigDecimal("50600.00"), history.get(0).balanceAfter());
    }

    private CashMovementRequest cashRequest(String amount, String idempotencyKey) {
        CashMovementRequest request = new CashMovementRequest();
        request.setAmount(new BigDecimal(amount));
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private BigDecimal balanceOf101() {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE acc_id = 101", BigDecimal.class);
    }

    private void insertOrder(Long orderId, String status, String idempotencyKey) {
        jdbcTemplate.update(
            """
            INSERT INTO orders (order_id, portfolio_id, instrument_id, side, quantity, price, status, idempotency_key, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            orderId,
            201L,
            301L,
            "BUY",
            10L,
            new BigDecimal("1200.0000"),
            status,
            idempotencyKey,
            OffsetDateTime.parse("2026-09-10T10:00:00Z"),
            OffsetDateTime.parse("2026-09-10T10:00:00Z")
        );
    }

    private void seedBaseData() {
        jdbcTemplate.update(
            """
            INSERT INTO accounts (acc_id, account_number, risk_profile, base_currency, balance, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            101L,
            "1000000001",
            "MODERATE",
            "INR",
            new BigDecimal("50000.00"),
            "ACTIVE",
            1L,
            OffsetDateTime.parse("2026-09-09T14:00:00Z"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );

        jdbcTemplate.update(
            """
            INSERT INTO clients (id, acc_id, name, email, phone, dob, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            1L,
            101L,
            "Priya Menon",
            "priya.menon@example.com",
            "9876500101",
            java.sql.Date.valueOf("1990-05-10"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );

        jdbcTemplate.update(
            "INSERT INTO portfolio (portfolio_id, name, account_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            201L,
            "Priya Portfolio",
            101L,
            "ACTIVE",
            OffsetDateTime.parse("2026-09-09T14:00:00Z"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );

        jdbcTemplate.update(
            "INSERT INTO instruments (instrument_id, name, ticker, asset_class, currency, exchange, tradable) VALUES (?, ?, ?, ?, ?, ?, ?)",
            301L,
            "Infosys Limited",
            "INFY.NS",
            "EQUITY",
            "INR",
            "NSE",
            true
        );

        jdbcTemplate.update(
            """
            INSERT INTO portfolio_holdings (portfolio_holding_id, portfolio_id, instrument_id, quantity, avg_cost, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            401L,
            201L,
            301L,
            100L,
            new BigDecimal("1450.0000"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );
    }

    private Long toPositiveLong(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return mixed == Long.MIN_VALUE ? 0L : Math.abs(mixed);
    }
}
