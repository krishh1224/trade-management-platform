package com.team5.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.domain.enums.OrderSide;
import com.team5.exception.DuplicateOrderException;
import com.team5.exception.OrderConflictException;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.OrderResponse;
import com.team5.service.OrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
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
              client_id BIGINT NOT NULL,
              account_number VARCHAR(10) NOT NULL UNIQUE,
              risk_profile VARCHAR(20) NOT NULL,
              base_currency VARCHAR(3) NOT NULL,
              balance NUMERIC(19,2) NOT NULL,
              status VARCHAR(20) NOT NULL,
              version BIGINT NOT NULL,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              CONSTRAINT fk_accounts_client FOREIGN KEY (client_id) REFERENCES clients(id)
            )
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

    @Test
    void placeOrderCommitsOrderBalanceHoldingsAndTransaction() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(101L);
        request.setSymbol("INFY.NS");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("1000.00"));
        request.setIdempotencyKey("idem-commit-1");

        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertTrue(response.orderId().startsWith("ORD-"));
        assertEquals("NEW", response.status().name());

        BigDecimal balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE acc_id = 101",
            BigDecimal.class
        );
        assertEquals(new BigDecimal("40000.00"), balance);

        Long version = jdbcTemplate.queryForObject(
            "SELECT version FROM accounts WHERE acc_id = 101",
            Long.class
        );
        assertEquals(2L, version);

        Long quantity = jdbcTemplate.queryForObject(
            "SELECT quantity FROM portfolio_holdings WHERE portfolio_id = 201 AND instrument_id = 301",
            Long.class
        );
        assertEquals(110L, quantity);

        BigDecimal avgCost = jdbcTemplate.queryForObject(
            "SELECT avg_cost FROM portfolio_holdings WHERE portfolio_id = 201 AND instrument_id = 301",
            BigDecimal.class
        );
        assertEquals(new BigDecimal("1409.0909"), avgCost.setScale(4, RoundingMode.HALF_UP));

        Integer orderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE idempotency_key = 'idem-commit-1'",
            Integer.class
        );
        assertEquals(1, orderCount);

        Integer transactionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE account_id = 101 AND transaction_type = 'BUY'",
            Integer.class
        );
        assertEquals(1, transactionCount);
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

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(request));

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
            () -> orderService.cancelOrder(orderUuid)
        );

        assertEquals("ORD-409", exception.getCode());
    }

    private void seedBaseData() {
        jdbcTemplate.update(
            """
            INSERT INTO clients (id, name, email, phone, dob, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            1L,
            "Priya Menon",
            "priya.menon@example.com",
            "9876500101",
            java.sql.Date.valueOf("1990-05-10"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );

        jdbcTemplate.update(
            """
            INSERT INTO accounts (acc_id, client_id, account_number, risk_profile, base_currency, balance, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            101L,
            1L,
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
