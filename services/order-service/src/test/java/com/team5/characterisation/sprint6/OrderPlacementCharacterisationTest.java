package com.team5.characterisation.sprint6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.hikari.schema=characterisation_sprint6"
})
@AutoConfigureMockMvc
class OrderPlacementCharacterisationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS characterisation_sprint6");
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

    @Test
    void affordableOrder_returnsAcceptedPayloadFieldByField() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .with(jwt().jwt(token -> token.claim("accountId", 101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(101L, "INFY.NS", "BUY", 10, "1000.00", "char-affordable-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").isString())
            .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.message").value("Order accepted"))
            .andExpect(jsonPath("$.symbol").value("INFY.NS"))
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.quantity").value(10))
            .andExpect(jsonPath("$.price").value(1000.0));
    }

    @Test
    void reusedIdempotencyKey_returnsOrd409Conflict() throws Exception {
        insertExistingOrder("char-dup-1");

        mockMvc.perform(post("/api/v1/orders")
                .with(jwt().jwt(token -> token.claim("accountId", 101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(101L, "INFY.NS", "BUY", 10, "1000.00", "char-dup-1")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("ORD-409"))
            .andExpect(jsonPath("$.message").value("Duplicate order"));
    }

    // Removed: unaffordableBuy_returnsOrd400BadRequest
    // This characterised the synchronous balance check that lived in the cash-update
    // logic removed from placeOrder() ("Trade REST API Publishes ORDER_PLACED and
    // Answers NEW"). There is no synchronous fill left to reject on insufficient funds —
    // affordability is the executor's concern once pricing happens. No equivalent here
    // until the executor exists; it will be re-covered by the executor's own tests.

    @Test
    void unknownSymbol_returnsIns404NotFound() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .with(jwt().jwt(token -> token.claim("accountId", 101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(101L, "UNKNOWN.NS", "BUY", 10, "1000.00", "char-unknown-symbol-1")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("INS-404"))
            .andExpect(jsonPath("$.message").value("Instrument not found"));
    }

    @Test
    void nonActiveAccount_returnsAcc403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .with(jwt().jwt(token -> token.claim("accountId", 102L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(102L, "INFY.NS", "BUY", 10, "1000.00", "char-inactive-account-1")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"))
            .andExpect(jsonPath("$.message").value("Account not active"));
    }

    // Renamed from acceptedOrder_pinsOrderRowCashAndPositionWrites, and the
    // balance/holdings pins it used to carry were dropped along with the synchronous
    // fill — see the comment on the removed unaffordableBuy_returnsOrd400BadRequest test
    // above. Cash/position pins move to the executor's own characterisation tests.
    @Test
    void acceptedOrder_pinsOrderRow() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .with(jwt().jwt(token -> token.claim("accountId", 101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(101L, "INFY.NS", "BUY", 10, "1000.00", "char-persistence-1")))
            .andExpect(status().isOk());

        String orderStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE idempotency_key = ?",
            String.class,
            "char-persistence-1"
        );
        assertEquals("NEW", orderStatus);

        String side = jdbcTemplate.queryForObject(
            "SELECT side FROM orders WHERE idempotency_key = ?",
            String.class,
            "char-persistence-1"
        );
        assertEquals("BUY", side);

        Long quantity = jdbcTemplate.queryForObject(
            "SELECT quantity FROM orders WHERE idempotency_key = ?",
            Long.class,
            "char-persistence-1"
        );
        assertEquals(10L, quantity);

        BigDecimal orderPrice = jdbcTemplate.queryForObject(
            "SELECT price FROM orders WHERE idempotency_key = ?",
            BigDecimal.class,
            "char-persistence-1"
        );
        assertNotNull(orderPrice);
        assertEquals(new BigDecimal("1000.0000"), orderPrice);
    }

    private static String orderBody(
        long accountId,
        String symbol,
        String side,
        int quantity,
        String price,
        String idempotencyKey
    ) {
        return """
            {
              "accountId": %d,
              "symbol": "%s",
              "side": "%s",
              "quantity": %d,
              "price": %s,
              "idempotencyKey": "%s"
            }
            """.formatted(accountId, symbol, side, quantity, price, idempotencyKey);
    }

    private void insertExistingOrder(String idempotencyKey) {
        jdbcTemplate.update(
            """
            INSERT INTO orders (order_id, portfolio_id, instrument_id, side, quantity, price, status, idempotency_key, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            900001L,
            201L,
            301L,
            "BUY",
            1L,
            new BigDecimal("100.0000"),
            "NEW",
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
            INSERT INTO accounts (acc_id, account_number, risk_profile, base_currency, balance, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            102L,
            "1000000002",
            "MODERATE",
            "INR",
            new BigDecimal("50000.00"),
            "SUSPENDED",
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
}
