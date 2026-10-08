package com.team5.watchlists.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.notifications.api.AlertDelivery;
import com.team5.notifications.api.AlertDirection;
import com.team5.watchlists.domain.LastQuote;
import com.team5.watchlists.domain.MarketQuote;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.PriceAlertStatus;
import com.team5.watchlists.domain.QuoteEvaluation;
import com.team5.watchlists.dto.PriceAlertResponse;
import com.team5.watchlists.dto.WatchlistItemResponse;
import com.team5.watchlists.service.PriceAlertEvaluator;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Runs the module's real SQL on Postgres, in a throwaway schema, building the tables from the
// migration files themselves. Needs the database from docker-compose.yml, like the other mapper tests.
@SpringBootTest(properties = "spring.datasource.hikari.schema=price_alert_test")
class PriceAlertMapperIntegrationTest {

    private static final Path MIGRATIONS = Path.of("../../databases/postgresql/migrations");
    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;
    private static final long INFY_ENTRY = 7001L;
    private static final long TCS_ENTRY = 7002L;
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-07T09:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PriceAlertMapper alertMapper;

    @Autowired
    private WatchlistQuoteMapper quoteMapper;

    @Autowired
    private WatchlistMapper watchlistMapper;

    @Autowired
    private PriceAlertEvaluator evaluator;

    @BeforeEach
    void createTables() throws Exception {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS price_alert_test");
        for (String table : List.of("notifications", "price_alerts", "watchlist_quotes", "market_quote_snapshots",
            "watchlists", "watchlist_groups", "instruments", "accounts")) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table + " CASCADE");
        }
        // just enough of the baseline schema for the foreign keys the migrations declare
        jdbcTemplate.execute("CREATE TABLE accounts (acc_id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("""
            CREATE TABLE instruments (
                instrument_id BIGINT PRIMARY KEY,
                ticker VARCHAR(20) NOT NULL UNIQUE,
                name VARCHAR(100) NOT NULL,
                currency VARCHAR(3) NOT NULL,
                tradable BOOLEAN NOT NULL DEFAULT TRUE
            )""");
        for (String migration : List.of("V7_add_watchlists.sql", "V8_watchlist_groups.sql",
            "V9_market_quote_snapshots.sql", "V13_notifications.sql", "V16_price_alerts.sql",
            "V17_price_alerts_unique_active.sql")) {
            jdbcTemplate.execute(Files.readString(MIGRATIONS.resolve(migration)));
        }
        // the migration is re-runnable: apply.sh runs every file on every deploy
        jdbcTemplate.execute(Files.readString(MIGRATIONS.resolve("V16_price_alerts.sql")));
        jdbcTemplate.execute(Files.readString(MIGRATIONS.resolve("V17_price_alerts_unique_active.sql")));

        jdbcTemplate.update("INSERT INTO accounts (acc_id) VALUES (?), (?)", OWNER, OTHER_ACCOUNT);
        jdbcTemplate.update("INSERT INTO instruments (instrument_id, ticker, name, currency) VALUES (301, 'INFY.NS', 'Infosys Limited', 'INR'), (302, 'TCS.NS', 'Tata Consultancy Services', 'INR')");
        jdbcTemplate.update("INSERT INTO watchlist_groups (watchlist_group_id, account_id, name) VALUES (5001, ?, 'Favourite'), (5002, ?, 'Favourite')", OWNER, OTHER_ACCOUNT);
        jdbcTemplate.update("INSERT INTO watchlists (watchlist_id, account_id, watchlist_group_id, instrument_id) VALUES (?, ?, 5001, 301), (?, ?, 5001, 302), (7003, ?, 5002, 301)",
            INFY_ENTRY, OWNER, TCS_ENTRY, OWNER, OTHER_ACCOUNT);
    }

    @Test
    void insertedAlert_readsBackWithItsStateAndInstrument() {
        PriceAlertRow alert = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500.0000");

        List<PriceAlertResponse> alerts = alertMapper.findByAccount(OWNER);

        assertEquals(1, alerts.size());
        PriceAlertResponse read = alerts.get(0);
        assertEquals(alert.getAlertId(), read.alertId());
        assertEquals(INFY_ENTRY, read.watchlistId());
        assertEquals("Infosys Limited", read.instrumentName());
        assertEquals("INR", read.currency());
        assertEquals(AlertDirection.ABOVE, read.direction());
        assertEquals(PriceAlertStatus.ACTIVE, read.status());
        assertEquals(0, new BigDecimal("1500").compareTo(read.threshold()));
        assertNull(read.triggeredAt());
        assertEquals(OWNER, alertMapper.findOwner(alert.getAlertId()));
        assertNull(alertMapper.findForAccount(alert.getAlertId(), OTHER_ACCOUNT), "never read across accounts");
        assertTrue(alertMapper.findByAccount(OTHER_ACCOUNT).isEmpty());
    }

    @Test
    void perQuoteLookup_findsOnlyTheSymbolsActiveAlertsBetweenThePrices() {
        PriceAlertRow inRange = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");
        PriceAlertRow atLowBound = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.BELOW, "1490");
        insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1600");     // out of range
        insert(OWNER, TCS_ENTRY, "TCS.NS", AlertDirection.ABOVE, "1500");       // other symbol
        PriceAlertRow triggered = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1495");
        alertMapper.markTriggered(triggered.getAlertId(), new BigDecimal("1496"), T0);

        List<PriceAlertRow> found = alertMapper.findActiveBySymbolBetween("INFY.NS", new BigDecimal("1490"), new BigDecimal("1510"));

        assertEquals(List.of(inRange.getAlertId(), atLowBound.getAlertId()), found.stream().map(PriceAlertRow::getAlertId).toList());
        assertEquals(inRange.getDeliveryId(), found.get(0).getDeliveryId());
        assertEquals(AlertDirection.ABOVE, found.get(0).getDirection());
    }

    @Test
    void perQuoteLookup_canUseThePartialIndexOnSymbol() {
        String plan = jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                StringBuilder lines = new StringBuilder();
                try (ResultSet rs = statement.executeQuery("""
                    EXPLAIN SELECT alert_id FROM price_alerts
                    WHERE symbol = 'INFY.NS' AND status = 'ACTIVE' AND threshold BETWEEN 1490 AND 1510""")) {
                    while (rs.next()) {
                        lines.append(rs.getString(1)).append('\n');
                    }
                }
                statement.execute("RESET enable_seqscan");
                return lines.toString();
            }
        });

        // On a near-empty table the planner may serve it from either partial index that covers symbol
        // (uq_price_alerts_active_level as well); with real data the symbol-led one wins. Either way the
        // lookup is an index scan on symbol, never a scan of every alert.
        assertTrue(plan.contains("Index") && plan.contains("symbol") && !plan.contains("Seq Scan"), plan);
        String definition = jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_price_alerts_active_symbol' AND schemaname = 'price_alert_test'",
            String.class);
        assertTrue(definition.contains("(symbol, threshold)") && definition.contains("WHERE"), definition);
    }

    @Test
    void markTriggered_happensOnce() {
        PriceAlertRow alert = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");

        assertEquals(1, alertMapper.markTriggered(alert.getAlertId(), new BigDecimal("1502.35"), T0));
        assertEquals(0, alertMapper.markTriggered(alert.getAlertId(), new BigDecimal("1600.00"), T0.plusMinutes(1)));

        PriceAlertResponse read = alertMapper.findForAccount(alert.getAlertId(), OWNER);
        assertEquals(PriceAlertStatus.TRIGGERED, read.status());
        assertEquals(0, new BigDecimal("1502.35").compareTo(read.triggerPrice()));
        assertEquals(T0.toInstant(), read.triggeredAt().toInstant());
    }

    @Test
    void activeCount_andTheAccountLock() {
        insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");
        PriceAlertRow second = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.BELOW, "1400");
        insert(OTHER_ACCOUNT, 7003L, "INFY.NS", AlertDirection.ABOVE, "1500");
        alertMapper.markTriggered(second.getAlertId(), new BigDecimal("1399"), T0);

        assertEquals(1, alertMapper.countActiveByAccount(OWNER), "triggered alerts do not count");
        Integer locked = new TransactionTemplate(transactionManager).execute(status -> alertMapper.lockAccountAlerts(OWNER));
        assertEquals(Integer.valueOf(1), locked);
    }

    @Test
    void sameActiveLevel_isFoundAndRefusedByTheDatabase() {
        PriceAlertRow first = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");

        assertTrue(alertMapper.existsActive(OWNER, "INFY.NS", AlertDirection.ABOVE, new BigDecimal("1500.0000")));
        assertFalse(alertMapper.existsActive(OWNER, "INFY.NS", AlertDirection.BELOW, new BigDecimal("1500")));
        assertFalse(alertMapper.existsActive(OTHER_ACCOUNT, "INFY.NS", AlertDirection.ABOVE, new BigDecimal("1500")));
        assertThrows(DuplicateKeyException.class,
            () -> insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500.00"));

        // once it has fired the level is free again
        alertMapper.markTriggered(first.getAlertId(), new BigDecimal("1501"), T0);
        assertFalse(alertMapper.existsActive(OWNER, "INFY.NS", AlertDirection.ABOVE, new BigDecimal("1500")));
        insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500.00");
    }

    @Test
    void v17_keepsTheOldestOfDuplicatesAlreadyStored() throws Exception {
        jdbcTemplate.execute("DROP INDEX uq_price_alerts_active_level");
        PriceAlertRow oldest = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");
        insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500.00");
        insert(OWNER, TCS_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");
        PriceAlertRow other = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.BELOW, "1500");

        jdbcTemplate.execute(Files.readString(MIGRATIONS.resolve("V17_price_alerts_unique_active.sql")));

        assertEquals(List.of(oldest.getAlertId(), other.getAlertId()),
            jdbcTemplate.queryForList("SELECT alert_id FROM price_alerts ORDER BY alert_id", Long.class));
    }

    @Test
    void deletingAWatchlistGroup_removesItsEntriesAndTheirAlerts() {
        jdbcTemplate.update("INSERT INTO watchlist_groups (watchlist_group_id, account_id, name) VALUES (5003, ?, 'Swing ideas')", OWNER);
        jdbcTemplate.update("INSERT INTO watchlists (watchlist_id, account_id, watchlist_group_id, instrument_id) VALUES (7004, ?, 5003, 301)", OWNER);
        PriceAlertRow inGroup = insert(OWNER, 7004L, "INFY.NS", AlertDirection.ABOVE, "1500");
        PriceAlertRow elsewhere = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.BELOW, "1400");

        assertEquals(0, watchlistMapper.deleteWatchlistGroup(OTHER_ACCOUNT, 5003L), "never across accounts");
        assertEquals(1, watchlistMapper.deleteWatchlistGroup(OWNER, 5003L));

        assertNull(watchlistMapper.findWatchlistGroup(5003L));
        assertNull(watchlistMapper.findWatchlistEntry(7004L));
        assertNull(alertMapper.findOwner(inGroup.getAlertId()));
        assertEquals(OWNER, alertMapper.findOwner(elsewhere.getAlertId()));
        assertEquals("Favourite", watchlistMapper.findWatchlistGroup(5001L).name());
    }

    @Test
    void deleteForAccount_onlyDeletesTheOwnersAlert() {
        PriceAlertRow alert = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");

        assertEquals(0, alertMapper.deleteForAccount(alert.getAlertId(), OTHER_ACCOUNT));
        assertEquals(1, alertMapper.deleteForAccount(alert.getAlertId(), OWNER));
        assertNull(alertMapper.findOwner(alert.getAlertId()));
    }

    @Test
    void removingTheWatchlistEntry_removesItsAlerts() {
        PriceAlertRow alert = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");

        assertEquals(1, watchlistMapper.deleteWatchlistItem(OWNER, INFY_ENTRY));

        assertNull(alertMapper.findOwner(alert.getAlertId()));
    }

    @Test
    void storedQuote_isInsertedOnceThenMovedOn() {
        assertEquals(1, quoteMapper.insertIfAbsent("INFY.NS", new BigDecimal("1490.0000"), T0));
        assertEquals(0, quoteMapper.insertIfAbsent("INFY.NS", new BigDecimal("1999.0000"), T0.plusMinutes(1)));

        LastQuote stored = new TransactionTemplate(transactionManager).execute(status -> quoteMapper.lockLastQuote("INFY.NS"));
        assertEquals(0, new BigDecimal("1490").compareTo(stored.lastPrice()));
        assertEquals(T0.toInstant(), stored.quotedAt().toInstant());

        quoteMapper.update("INFY.NS", new BigDecimal("1510.0000"), T0.plusMinutes(2));
        assertEquals(0, new BigDecimal("1510").compareTo(quoteMapper.lockLastQuote("INFY.NS").lastPrice()));
    }

    @Test
    void watchlistShowsTheConsumedPrice_andTheExecutorSnapshotUntilThereIsOne() {
        jdbcTemplate.update("INSERT INTO market_quote_snapshots (symbol, last_price, change_pct) VALUES ('INFY.NS', 1480.0000, 1.2500), ('TCS.NS', 3900.0000, -0.5000)");
        quoteMapper.insertIfAbsent("INFY.NS", new BigDecimal("1502.3500"), T0);

        Map<String, WatchlistItemResponse> bySymbol = watchlistMapper.findWatchlistByAccountId(OWNER, null).stream()
            .collect(java.util.stream.Collectors.toMap(WatchlistItemResponse::symbol, item -> item));

        assertEquals(0, new BigDecimal("1502.35").compareTo(bySymbol.get("INFY.NS").lastPrice()), "consumed from market-data");
        assertEquals(0, new BigDecimal("1.25").compareTo(bySymbol.get("INFY.NS").changePct()));
        assertEquals("INR", bySymbol.get("INFY.NS").currency());
        assertEquals(0, new BigDecimal("3900").compareTo(bySymbol.get("TCS.NS").lastPrice()), "snapshot fallback");
    }

    @Test
    void crossingQuote_triggersTheAlertAndRecordsAPriceAlertNotification() {
        PriceAlertRow alert = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");

        evaluator.evaluate(new MarketQuote(UUID.randomUUID(), "INFY.NS", new BigDecimal("1490.0000"), T0));
        QuoteEvaluation result = evaluator.evaluate(new MarketQuote(UUID.randomUUID(), "INFY.NS", new BigDecimal("1502.3500"), T0.plusMinutes(1)));
        evaluator.evaluate(new MarketQuote(UUID.randomUUID(), "INFY.NS", new BigDecimal("1510.0000"), T0.plusMinutes(2)));

        assertEquals(1, result.triggered());
        assertEquals(PriceAlertStatus.TRIGGERED, alertMapper.findForAccount(alert.getAlertId(), OWNER).status());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT event_id, account_id, type, status, message FROM notifications");
        assertEquals(1, rows.size(), "one notification, however many quotes stay above");
        assertEquals(alert.getDeliveryId(), rows.get(0).get("event_id"));
        assertEquals(OWNER, ((Number) rows.get(0).get("account_id")).longValue());
        assertEquals("PRICE_ALERT", rows.get(0).get("type"));
        assertEquals("QUEUED", rows.get(0).get("status"));
        assertEquals("INFY.NS rose above your alert price of 1500.00. Last price 1502.35.", rows.get(0).get("message"));
    }

    @Test
    void notificationsFailing_rollsTheQuoteBack_soTheCrossingIsFoundOnRetry() {
        PriceAlertRow alert = insert(OWNER, INFY_ENTRY, "INFY.NS", AlertDirection.ABOVE, "1500");
        AlertDelivery refusing = priceAlert -> {
            throw new IllegalStateException("notifications unavailable");
        };
        PriceAlertEvaluator failing = new PriceAlertEvaluator(quoteMapper, alertMapper, refusing);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        MarketQuote crossing = new MarketQuote(UUID.randomUUID(), "INFY.NS", new BigDecimal("1502.3500"), T0.plusMinutes(1));
        evaluator.evaluate(new MarketQuote(UUID.randomUUID(), "INFY.NS", new BigDecimal("1490.0000"), T0));

        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> failing.evaluate(crossing)));

        // nothing moved: still ACTIVE, still measured from 1490
        assertEquals(PriceAlertStatus.ACTIVE, alertMapper.findForAccount(alert.getAlertId(), OWNER).status());
        assertEquals(0, new BigDecimal("1490").compareTo(quoteMapper.lockLastQuote("INFY.NS").lastPrice()));

        // the redelivered quote, with notifications back, triggers it
        assertEquals(1, evaluator.evaluate(crossing).triggered());
    }

    // Each mapper writes only the module's own tables. The notifications table is never written here:
    // a triggered alert reaches it only through AlertDelivery.
    @Test
    void theModulesMappersWriteOnlyItsOwnTables() throws Exception {
        assertWritesOnly("/mapper/PriceAlertMapper.xml", Set.of("price_alerts"));
        assertWritesOnly("/mapper/WatchlistQuoteMapper.xml", Set.of("watchlist_quotes"));
        assertWritesOnly("/mapper/WatchlistMapper.xml", Set.of("watchlists", "watchlist_groups"));
    }

    private void assertWritesOnly(String resource, Set<String> tables) throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // SQL statements only: the lookbehind skips the <update id="..."> element itself
        Matcher writes = Pattern.compile("(?i)(?<![<\\w])(insert\\s+into|update|delete\\s+from)\\s+(\\w+)").matcher(xml);
        int found = 0;
        while (writes.find()) {
            found++;
            assertTrue(tables.contains(writes.group(2).toLowerCase()), resource + " writes to " + writes.group(2));
        }
        assertTrue(found > 0, resource);
        assertFalse(Pattern.compile("(?i)\\b(truncate|alter\\s+table|drop\\s+table)\\b").matcher(xml).find(), resource);
    }

    private PriceAlertRow insert(long accountId, long watchlistId, String symbol, AlertDirection direction, String threshold) {
        PriceAlertRow row = new PriceAlertRow();
        row.setAccountId(accountId);
        row.setWatchlistId(watchlistId);
        row.setSymbol(symbol);
        row.setDirection(direction);
        row.setThreshold(new BigDecimal(threshold));
        row.setStatus(PriceAlertStatus.ACTIVE);
        row.setDeliveryId(UUID.randomUUID());
        row.setCreatedAt(T0);
        assertEquals(1, alertMapper.insert(row));
        return row;
    }
}
