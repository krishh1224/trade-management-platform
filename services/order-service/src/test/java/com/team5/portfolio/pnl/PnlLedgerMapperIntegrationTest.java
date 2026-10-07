package com.team5.portfolio.pnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Runs the real ledger SQL on Postgres, in a throwaway schema, using the migration file itself to build the table.
@SpringBootTest(properties = "spring.datasource.hikari.schema=pnl_ledger_test")
class PnlLedgerMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PnlLedgerMapper mapper;

    @BeforeEach
    void createLedger() throws Exception {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS pnl_ledger_test");
        jdbcTemplate.execute("DROP TABLE IF EXISTS realised_pnl_ledger CASCADE");
        jdbcTemplate.execute(Files.readString(Path.of("../../databases/postgresql/migrations/V15_realised_pnl_ledger.sql")));
    }

    @Test
    void bookingTheSameSaleTwiceInsertsOnlyOnce() {
        assertEquals(1, mapper.insertIfAbsent(entry(1, 101, "INFY.NS", "250.00", "2026-09-27T16:51:06")));
        assertEquals(0, mapper.insertIfAbsent(entry(1, 101, "INFY.NS", "999.00", "2026-09-27T16:51:06")));

        // the first booking stands: the second attempt, with a different figure, changed nothing
        assertEquals(0, new BigDecimal("250.00").compareTo(mapper.sumRealised(101, null, null)));
        assertEquals(List.of(1L), mapper.findBookedOrderIds(101));
    }

    @Test
    void accountsAreKeptApart() {
        mapper.insertIfAbsent(entry(1, 101, "INFY.NS", "250.00", "2026-09-27T16:51:06"));
        mapper.insertIfAbsent(entry(2, 102, "INFY.NS", "70.00", "2026-09-27T16:51:06"));

        assertEquals(0, new BigDecimal("250.00").compareTo(mapper.sumRealised(101, null, null)));
        assertEquals(List.of(2L), mapper.findBookedOrderIds(102));
    }

    @Test
    void anAccountWithNoSalesSumsToZeroNotNull() {
        assertEquals(0, BigDecimal.ZERO.compareTo(mapper.sumRealised(555, null, null)));
    }

    @Test
    void theDateBoundsIncludeTheFromInstantAndExcludeTheToInstant() {
        mapper.insertIfAbsent(entry(1, 101, "A", "10.00", "2026-09-01T00:00:00"));
        mapper.insertIfAbsent(entry(2, 101, "A", "20.00", "2026-09-30T23:59:59"));
        mapper.insertIfAbsent(entry(3, 101, "A", "40.00", "2026-10-01T00:00:00"));
        mapper.insertIfAbsent(entry(4, 101, "A", "80.00", "2026-08-31T23:59:59"));

        BigDecimal september = mapper.sumRealised(101, LocalDateTime.parse("2026-09-01T00:00:00"),
            LocalDateTime.parse("2026-10-01T00:00:00"));

        assertEquals(0, new BigDecimal("30.00").compareTo(september));
        assertEquals(0, new BigDecimal("150.00").compareTo(mapper.sumRealised(101, null, null)));
        assertEquals(0, new BigDecimal("70.00").compareTo(
            mapper.sumRealised(101, LocalDateTime.parse("2026-09-01T00:00:00"), null)));
        assertEquals(0, new BigDecimal("110.00").compareTo(
            mapper.sumRealised(101, null, LocalDateTime.parse("2026-10-01T00:00:00"))));
    }

    @Test
    void theBreakdownGroupsBySymbolInOrder() {
        mapper.insertIfAbsent(entry(1, 101, "ITC.NS", "-5.00", "2026-09-27T10:00:00"));
        mapper.insertIfAbsent(entry(2, 101, "INFY.NS", "250.00", "2026-09-27T10:00:00"));
        mapper.insertIfAbsent(entry(3, 101, "INFY.NS", "50.00", "2026-09-28T10:00:00"));

        List<SymbolRealised> rows = mapper.sumRealisedBySymbol(101, null, null);

        assertEquals(List.of("INFY.NS", "ITC.NS"), rows.stream().map(SymbolRealised::symbol).toList());
        assertEquals(0, new BigDecimal("300.00").compareTo(rows.get(0).realisedPnl()));
        assertEquals(0, new BigDecimal("-5.00").compareTo(rows.get(1).realisedPnl()));
    }

    // The ledger is the only table the module may write. Its mapper must name no other table in a write.
    @Test
    void theLedgerMapperWritesNothingButTheLedgerTable() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/mapper/PnlLedgerMapper.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher writes = Pattern.compile("(?i)\\b(insert\\s+into|update|delete\\s+from)\\s+(\\w+)").matcher(xml);

        int found = 0;
        while (writes.find()) {
            found++;
            assertEquals("realised_pnl_ledger", writes.group(2).toLowerCase(), "writes to " + writes.group(2));
        }
        assertTrue(found > 0, "expected the ledger insert");
        assertFalse(Pattern.compile("(?i)\\b(truncate|alter\\s+table|drop\\s+table)\\b").matcher(xml).find());
    }

    private static LedgerEntry entry(long orderId, long accountId, String symbol, String realisedBase, String soldAt) {
        return new LedgerEntry(orderId, accountId, symbol, "INR", "INR", 5, new BigDecimal("1550.0000"),
            new BigDecimal("1500.0000"), BigDecimal.ONE.setScale(8), new BigDecimal(realisedBase).setScale(4),
            new BigDecimal(realisedBase), LocalDateTime.parse(soldAt));
    }
}
