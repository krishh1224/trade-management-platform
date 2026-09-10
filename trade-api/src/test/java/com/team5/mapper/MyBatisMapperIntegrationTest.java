package com.team5.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.request.PlaceOrderRequest;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.OrderResponse;
import com.team5.dto.response.PositionResponse;
import com.team5.service.OrderService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.hikari.schema=mapper_test"
})
class MyBatisMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderCommandMapper orderCommandMapper;

    @Autowired
    private AccountQueryMapper accountQueryMapper;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS mapper_test");
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
              side VARCHAR(8) NOT NULL,
              quantity BIGINT NOT NULL CHECK (quantity > 0),
              price NUMERIC(19,4) NOT NULL CHECK (price > 0),
              status VARCHAR(20) NOT NULL,
              idempotency_key VARCHAR(100) NOT NULL UNIQUE,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              CONSTRAINT fk_orders_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(portfolio_id),
              CONSTRAINT fk_orders_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
            )
            """);

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
            INSERT INTO clients (id, name, email, phone, dob, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            2L,
            "Ravi Shah",
            "ravi.shah@example.com",
            "9876500202",
            java.sql.Date.valueOf("1988-03-15"),
            OffsetDateTime.parse("2026-09-09T15:00:00Z"),
            OffsetDateTime.parse("2026-09-09T15:00:00Z")
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
            new BigDecimal("95000.00"),
            "ACTIVE",
            7L,
            OffsetDateTime.parse("2026-09-09T14:00:00Z"),
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );

        jdbcTemplate.update(
            """
            INSERT INTO accounts (acc_id, client_id, account_number, risk_profile, base_currency, balance, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            202L,
            2L,
            "1000000002",
            "LOW",
            "INR",
            new BigDecimal("50000.00"),
            "ACTIVE",
            3L,
            OffsetDateTime.parse("2026-09-09T15:00:00Z"),
            OffsetDateTime.parse("2026-09-09T15:00:00Z")
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
            "INSERT INTO portfolio (portfolio_id, name, account_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            202L,
            "Ravi Portfolio",
            202L,
            "ACTIVE",
            OffsetDateTime.parse("2026-09-09T15:00:00Z"),
            OffsetDateTime.parse("2026-09-09T15:00:00Z")
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
            "INSERT INTO instruments (instrument_id, name, ticker, asset_class, currency, exchange, tradable) VALUES (?, ?, ?, ?, ?, ?, ?)",
            302L,
            "Tata Consultancy Services",
            "TCS.NS",
            "EQUITY",
            "INR",
            "NSE",
            true
        );

        jdbcTemplate.update(
            "INSERT INTO instruments (instrument_id, name, ticker, asset_class, currency, exchange, tradable) VALUES (?, ?, ?, ?, ?, ?, ?)",
            303L,
            "Malicious Symbol Instrument",
            "AAPL' OR '1'='1",
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

    @Test
    void orderRowCanBeInsertedAndRetrieved_withMaliciousSymbolStillBound() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-09-10T10:15:30Z");
        String maliciousSymbol = "AAPL' OR '1'='1";

        int insertedRows = orderCommandMapper.insertOrder(
            "ORD-1001",
            101L,
            maliciousSymbol,
            "BUY",
            10,
            new BigDecimal("1500.00"),
            "NEW",
            "idem-1001",
            createdAt,
            createdAt
        );

        assertEquals(1, insertedRows);

        OrderResponse persisted = orderCommandMapper.findOrderByOrderId("ORD-1001");
        assertNotNull(persisted);
        assertEquals(maliciousSymbol, persisted.symbol());

        List<OrderHistoryEntry> accountOrders = accountQueryMapper.getOrders(
            101L,
            null,
            createdAt.minusDays(1),
            createdAt.plusDays(1)
        );
        assertEquals(1, accountOrders.size());
        assertEquals(maliciousSymbol, accountOrders.get(0).symbol());
    }

    @Test
    void accountAndPositionsAreReadCorrectly() {
        AccountResponse account = accountQueryMapper.getAccount(101L);
        assertNotNull(account);
        assertEquals(101L, account.id());
        assertEquals("1000000001", account.accountId());
        assertEquals(AccountStatus.ACTIVE, account.status());

        BalanceResponse balance = accountQueryMapper.getBalance(101L);
        assertNotNull(balance);
        assertEquals(101L, balance.accountId());
        assertEquals(new BigDecimal("95000.00"), balance.cashBalance());
        assertEquals("INR", balance.currency());

        List<PositionResponse> positions = accountQueryMapper.getPositions(101L);
        assertEquals(1, positions.size());
        assertEquals("INFY.NS", positions.get(0).symbol());
        assertEquals(100, positions.get(0).quantity());
    }

    @Test
    void insertAndUpdateOperationsReportAffectedRowCount() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-10T11:00:00Z");

        int insertCount = orderCommandMapper.insertOrder(
            "ORD-2001",
            101L,
            "TCS.NS",
            "BUY",
            5,
            new BigDecimal("3300.00"),
            "NEW",
            "idem-2001",
            now,
            now
        );
        assertEquals(1, insertCount);

        int updateCount = orderCommandMapper.updateOrderStatus(
            "ORD-2001",
            "CANCELLED",
            now.plusMinutes(1)
        );
        assertEquals(1, updateCount);

        OrderResponse updated = orderCommandMapper.findOrderByOrderId("ORD-2001");
        assertNotNull(updated);
        assertEquals(OrderStatus.CANCELLED, updated.status());
    }

    @Test
    void constraintViolationIsSurfacedToCaller() {
        PlaceOrderRequest first = new PlaceOrderRequest();
        first.setAccountId(101L);
        first.setSymbol("INFY.NS");
        first.setSide(OrderSide.BUY);
        first.setQuantity(10);
        first.setPrice(new BigDecimal("1500.00"));
        first.setIdempotencyKey("duplicate-idempotency");

        PlaceOrderRequest duplicate = new PlaceOrderRequest();
        duplicate.setAccountId(101L);
        duplicate.setSymbol("INFY.NS");
        duplicate.setSide(OrderSide.BUY);
        duplicate.setQuantity(10);
        duplicate.setPrice(new BigDecimal("1500.00"));
        duplicate.setIdempotencyKey("duplicate-idempotency");

        orderService.placeOrder(first);

        assertThrows(DataAccessException.class, () -> orderService.placeOrder(duplicate));
    }
}
