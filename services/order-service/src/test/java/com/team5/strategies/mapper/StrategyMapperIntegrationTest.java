package com.team5.strategies.mapper;

import static com.team5.strategies.StrategyFixtures.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team5.strategies.domain.ExecutionOutcome;
import com.team5.strategies.domain.ExecutionRow;
import com.team5.strategies.domain.PositionSnapshot;
import com.team5.strategies.domain.SignalResult;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.domain.StrategyType;
import com.team5.strategies.engine.OrderPlacement;
import com.team5.strategies.engine.StrategyExecutor;
import com.team5.strategies.engine.TradeApiOrderClient;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Runs the module's real SQL on Postgres, in a throwaway schema, building its tables from the migration
// file itself. Needs the database from docker-compose.yml, like the other mapper tests. The Trade API
// call is the only thing replaced.
@SpringBootTest(properties = "spring.datasource.hikari.schema=strategy_test")
class StrategyMapperIntegrationTest {

    private static final Path MIGRATIONS = Path.of("../../databases/postgresql/migrations");
    private static final long OWNER = 101L;
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-08T09:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StrategyMapper strategyMapper;

    @Autowired
    private StrategyExecutionMapper executionMapper;

    @Autowired
    private StrategyQuoteMapper quoteMapper;

    @Autowired
    private StrategyReadMapper readMapper;

    @Autowired
    private StrategyExecutor executor;

    @MockBean
    private TradeApiOrderClient orderClient;

    @BeforeEach
    void createTables() throws Exception {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS strategy_test");
        for (String table : List.of("strategy_executions", "strategy_quotes", "strategies", "orders",
            "portfolio_holdings", "portfolio", "instruments", "accounts")) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table + " CASCADE");
        }
        // just enough of the baseline schema for the foreign keys and the read-only queries
        jdbcTemplate.execute("CREATE TABLE accounts (acc_id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("""
            CREATE TABLE instruments (
                instrument_id BIGINT PRIMARY KEY,
                ticker VARCHAR(20) NOT NULL UNIQUE,
                tradable BOOLEAN NOT NULL DEFAULT TRUE
            )""");
        jdbcTemplate.execute("CREATE TABLE portfolio (portfolio_id BIGINT PRIMARY KEY, account_id BIGINT NOT NULL)");
        jdbcTemplate.execute("""
            CREATE TABLE portfolio_holdings (
                portfolio_holding_id BIGINT PRIMARY KEY,
                portfolio_id BIGINT NOT NULL,
                instrument_id BIGINT NOT NULL,
                quantity BIGINT NOT NULL
            )""");
        jdbcTemplate.execute("""
            CREATE TABLE orders (
                order_id BIGINT PRIMARY KEY,
                portfolio_id BIGINT NOT NULL,
                instrument_id BIGINT NOT NULL,
                quantity BIGINT NOT NULL,
                status VARCHAR(30) NOT NULL,
                side VARCHAR(10) NOT NULL,
                idempotency_key VARCHAR(100) NOT NULL UNIQUE,
                external_order_id VARCHAR(50)
            )""");
        jdbcTemplate.execute(Files.readString(MIGRATIONS.resolve("V18_strategies.sql")));
        // the migration is re-runnable: apply.sh runs every file on every deploy
        jdbcTemplate.execute(Files.readString(MIGRATIONS.resolve("V18_strategies.sql")));

        jdbcTemplate.update("INSERT INTO accounts (acc_id) VALUES (?)", OWNER);
        jdbcTemplate.update("INSERT INTO instruments (instrument_id, ticker, tradable) VALUES (301, 'INFY.NS', TRUE), (302, 'OLD.NS', FALSE)");
        jdbcTemplate.update("INSERT INTO portfolio (portfolio_id, account_id) VALUES (401, ?)", OWNER);
    }

    @Test
    void insertedStrategy_readsBack_andOnlyEnabledOnesAreEvaluated() {
        StrategyRow enabled = insert(StrategyStatus.ENABLED);
        StrategyRow disabled = insert(StrategyStatus.DISABLED);

        StrategyRow read = strategyMapper.findForAccount(enabled.getStrategyId(), OWNER);
        assertEquals(StrategyType.MA_CROSSOVER, read.getStrategyType());
        assertEquals(0, new BigDecimal("15000").compareTo(read.getMaxSpend()));
        assertEquals(0, BigDecimal.ZERO.compareTo(read.getSpent()));
        assertEquals(OWNER, strategyMapper.findOwner(enabled.getStrategyId()));
        assertNull(strategyMapper.findForAccount(enabled.getStrategyId(), 202L), "never read across accounts");

        assertEquals(List.of(enabled.getStrategyId()),
            strategyMapper.findEnabledBySymbol("INFY.NS").stream().map(StrategyRow::getStrategyId).toList());
        assertEquals(2, strategyMapper.countByAccount(OWNER));
        assertEquals(StrategyStatus.DISABLED, strategyMapper.findForAccount(disabled.getStrategyId(), OWNER).getStatus());
    }

    @Test
    void failuresInARow_haltTheStrategyAtItsLimit() {
        StrategyRow strategy = insert(StrategyStatus.ENABLED);
        long id = strategy.getStrategyId();

        strategyMapper.recordFailure(id, BigDecimal.ZERO, "Halted after 3", T0);
        strategyMapper.recordFailure(id, BigDecimal.ZERO, "Halted after 3", T0);
        assertEquals(StrategyStatus.ENABLED, strategyMapper.findForAccount(id, OWNER).getStatus());

        strategyMapper.recordFailure(id, BigDecimal.ZERO, "Halted after 3", T0);
        StrategyRow halted = strategyMapper.findForAccount(id, OWNER);
        assertEquals(StrategyStatus.HALTED, halted.getStatus());
        assertEquals("Halted after 3", halted.getStatusReason());
        assertEquals(3, halted.getConsecutiveFailures());
        assertTrue(strategyMapper.findEnabledBySymbol("INFY.NS").isEmpty());
    }

    @Test
    void anOrderPlaced_clearsTheFailureRun() {
        long id = insert(StrategyStatus.ENABLED).getStrategyId();
        strategyMapper.recordFailure(id, BigDecimal.ZERO, "x", T0);
        strategyMapper.recordFailure(id, BigDecimal.ZERO, "x", T0);

        strategyMapper.recordPlaced(id, new BigDecimal("1000.00"), T0);

        StrategyRow row = strategyMapper.findForAccount(id, OWNER);
        assertEquals(0, row.getConsecutiveFailures());
        assertEquals(1, row.getOrdersPlaced());
        assertEquals(0, new BigDecimal("1000.00").compareTo(row.getSpent()));
    }

    @Test
    void spendingPastTheCap_isRefusedByTheDatabaseToo() {
        long id = insert(StrategyStatus.ENABLED).getStrategyId();

        assertThrows(DataIntegrityViolationException.class,
            () -> strategyMapper.recordPlaced(id, new BigDecimal("15000.01"), T0));
    }

    @Test
    void parametersThatDoNotFitTheType_areRefusedByTheDatabase() {
        StrategyRow row = strategyRow(StrategyStatus.ENABLED);
        row.setBandWindow(20);

        assertThrows(DataIntegrityViolationException.class, () -> strategyMapper.insert(row));
    }

    @Test
    void executionsAreUniquePerStrategyAndQuote() {
        long id = insert(StrategyStatus.ENABLED).getStrategyId();
        UUID quoteId = UUID.randomUUID();
        ExecutionRow execution = execution(id, quoteId);
        executionMapper.insert(execution);

        assertTrue(executionMapper.exists(id, quoteId));
        assertFalse(executionMapper.exists(id, UUID.randomUUID()));
        assertEquals(quoteId, executionMapper.findByStrategy(id, 10).get(0).getQuoteEventId());
        assertThrows(DataIntegrityViolationException.class, () -> executionMapper.insert(execution(id, quoteId)));
    }

    @Test
    void priceSeries_newestFirst_upToTheQuote_andPrunedToTheNewest() {
        for (int i = 0; i < 6; i++) {
            quoteMapper.insert("INFY.NS", T0.plusMinutes(i), BigDecimal.valueOf(100 + i), UUID.randomUUID());
        }

        List<BigDecimal> recent = quoteMapper.findRecentPrices("INFY.NS", T0.plusMinutes(4), 3);
        assertEquals(List.of(104, 103, 102), recent.stream().map(BigDecimal::intValue).toList());
        assertEquals(T0.plusMinutes(5).toInstant(), quoteMapper.findLatest("INFY.NS").getQuotedAt().toInstant());

        List<StrategyQuoteMapper.StoredQuote> forChart = quoteMapper.findRecentQuotes("INFY.NS", 2);
        assertEquals(List.of(105, 104), forChart.stream().map(q -> q.getPrice().intValue()).toList());
        assertEquals(T0.plusMinutes(5).toInstant(), forChart.get(0).getQuotedAt().toInstant());

        quoteMapper.prune("INFY.NS", 4);
        assertEquals(List.of(105, 104, 103, 102),
            quoteMapper.findRecentPrices("INFY.NS", T0.plusDays(1), 10).stream().map(BigDecimal::intValue).toList());
    }

    @Test
    void position_isHoldingsPlusUnexecutedOrders() {
        jdbcTemplate.update("INSERT INTO portfolio_holdings VALUES (1, 401, 301, 30)");
        jdbcTemplate.update("""
            INSERT INTO orders (order_id, portfolio_id, instrument_id, quantity, status, side, idempotency_key, external_order_id)
            VALUES (1, 401, 301, 15, 'NEW', 'BUY', 'k1', 'ORD-1'),
                   (2, 401, 301, 4, 'NEW', 'SELL', 'k2', 'ORD-2'),
                   (3, 401, 301, 99, 'FILLED', 'BUY', 'k3', 'ORD-3')""");

        PositionSnapshot position = readMapper.findPosition(OWNER, "INFY.NS");

        assertEquals(30, position.getHeld());
        assertEquals(15, position.getPendingBuy());
        assertEquals(4, position.getPendingSell());
        assertEquals(0, readMapper.findPosition(OWNER, "TCS.NS").getHeld());
        assertEquals("ORD-2", readMapper.findOrderIdByIdempotencyKey("k2"));
        assertEquals("INFY.NS", readMapper.findTradableSymbol("INFY.NS"));
        assertNull(readMapper.findTradableSymbol("OLD.NS"));
    }

    // The live demonstration, made repeatable: the customer disables a strategy while its order is being
    // sent. The disable waits for that one decision, and once it returns nothing more is placed.
    @Test
    void disableDuringAnOrderDecision_waitsForIt_andNoOrderStartsAfterItReturns() throws Exception {
        long id = insert(StrategyStatus.ENABLED).getStrategyId();
        CountDownLatch orderInFlight = new CountDownLatch(1);
        AtomicLong orderAnsweredAt = new AtomicLong();
        when(orderClient.place(anyLong(), any())).thenAnswer(invocation -> {
            orderInFlight.countDown();
            Thread.sleep(500);
            orderAnsweredAt.set(System.nanoTime());
            return OrderPlacement.accepted(200, "ORD-in-flight");
        });

        CompletableFuture<ExecutionRow> decision = CompletableFuture.supplyAsync(
            () -> executor.execute(id, SignalResult.buy("test"), quote("100.00")));
        assertTrue(orderInFlight.await(5, TimeUnit.SECONDS));

        // the customer's disable, in its own transaction, as StrategyService runs it
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            strategyMapper.updateStatus(id, OWNER, StrategyStatus.DISABLED, "Disabled by the customer", false, T0));
        long disableReturnedAt = System.nanoTime();

        assertEquals(ExecutionOutcome.PLACED, decision.get(5, TimeUnit.SECONDS).getOutcome());
        assertTrue(disableReturnedAt > orderAnsweredAt.get(), "the disable waited for the decision in progress");

        // the next signal finds the strategy disabled: no order is sent
        ExecutionRow next = executor.execute(id, SignalResult.buy("test"), quote("100.00"));
        assertEquals(ExecutionOutcome.SKIPPED_DISABLED, next.getOutcome());
        verify(orderClient, times(1)).place(anyLong(), any());
    }

    private StrategyRow insert(StrategyStatus status) {
        StrategyRow row = strategyRow(status);
        assertEquals(1, strategyMapper.insert(row));
        return row;
    }

    private static StrategyRow strategyRow(StrategyStatus status) {
        StrategyRow row = new StrategyRow();
        row.setAccountId(OWNER);
        row.setName("INFY trend");
        row.setSymbol("INFY.NS");
        row.setStrategyType(StrategyType.MA_CROSSOVER);
        row.setShortWindow(2);
        row.setLongWindow(4);
        row.setQuantity(10);
        row.setMaxSpend(new BigDecimal("15000.00"));
        row.setMaxPosition(50L);
        row.setMaxConsecutiveFailures(3);
        row.setStatus(status);
        row.setCreatedAt(T0);
        return row;
    }

    private static ExecutionRow execution(long strategyId, UUID quoteId) {
        ExecutionRow execution = new ExecutionRow();
        execution.setStrategyId(strategyId);
        execution.setQuoteEventId(quoteId);
        execution.setSignal(com.team5.domain.enums.OrderSide.BUY);
        execution.setOutcome(ExecutionOutcome.SKIPPED_SPEND_LIMIT);
        execution.setQuotePrice(new BigDecimal("100.00"));
        execution.setCreatedAt(T0);
        return execution;
    }
}
