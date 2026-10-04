package com.team5.advisor.data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries over the shared trade_management database (the connection pool is read-only).
 *
 * Prices come from market_quote_snapshots, written by the Trade Executor's market-data poller;
 * FX rates come from the last settled order in that currency. Nothing here calls Fauxnance,
 * so the advisor uses none of the 2,000-requests-a-day quota.
 */
@Repository
public class AdvisorRepository {

    private final JdbcClient jdbc;

    public AdvisorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record AccountRow(long accId, String baseCurrency, BigDecimal balance, String status) {
    }

    /** A held instrument with its latest snapshot price (price columns null when never polled). */
    public record HoldingRow(
        String symbol, String name, String exchange, String currency, long quantity, BigDecimal avgCost,
        BigDecimal lastPrice, BigDecimal changePct, LocalDateTime priceUpdatedAt, Long priceAgeMinutes
    ) {
    }

    /** An instrument with its latest snapshot price (price columns null when never polled). */
    public record InstrumentRow(
        String symbol, String name, String exchange, String currency, String assetClass, boolean tradable,
        BigDecimal lastPrice, BigDecimal changePct, LocalDateTime priceUpdatedAt, Long priceAgeMinutes
    ) {
    }

    public record FxRow(BigDecimal rate, LocalDateTime asOf) {
    }

    public record OrderRow(
        String symbol, String side, long quantity, BigDecimal limitPrice, BigDecimal executedPrice, String status,
        String instrumentCurrency, BigDecimal settlementAmount, String settlementCurrency, String rejectionReason,
        LocalDateTime createdOn
    ) {
    }

    private static final String PRICE_COLUMNS = """
        s.last_price AS last_price,
        s.change_pct AS change_pct,
        s.updated_at AS price_updated_at,
        CAST(FLOOR(EXTRACT(EPOCH FROM (LOCALTIMESTAMP - s.updated_at)) / 60) AS BIGINT) AS price_age_minutes
        """;

    public Optional<AccountRow> findAccount(long accountId) {
        return jdbc.sql("""
                SELECT acc_id, base_currency, balance, status
                FROM accounts
                WHERE acc_id = :accountId
                """)
            .param("accountId", accountId)
            .query(AccountRow.class)
            .optional();
    }

    public List<HoldingRow> findHoldings(long accountId) {
        return jdbc.sql("""
                SELECT i.ticker AS symbol, i.name, i.exchange, i.currency,
                       ph.quantity, ph.avg_cost,
                """ + PRICE_COLUMNS + """
                FROM portfolio_holdings ph
                JOIN portfolio p ON p.portfolio_id = ph.portfolio_id
                JOIN instruments i ON i.instrument_id = ph.instrument_id
                LEFT JOIN market_quote_snapshots s ON s.symbol = i.ticker
                WHERE p.account_id = :accountId
                  AND ph.quantity > 0
                ORDER BY i.ticker
                """)
            .param("accountId", accountId)
            .query(HoldingRow.class)
            .list();
    }

    public List<InstrumentRow> findInstruments(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT i.ticker AS symbol, i.name, i.exchange, i.currency, i.asset_class, i.tradable,
                """ + PRICE_COLUMNS + """
                FROM instruments i
                LEFT JOIN market_quote_snapshots s ON s.symbol = i.ticker
                WHERE UPPER(i.ticker) IN (:symbols)
                ORDER BY i.ticker
                """)
            .param("symbols", symbols)
            .query(InstrumentRow.class)
            .list();
    }

    /** Matches the start of a word in the name, or the ticker anywhere. */
    public List<InstrumentRow> searchInstruments(String query, int limit) {
        String needle = query.trim().toUpperCase();
        return jdbc.sql("""
                SELECT i.ticker AS symbol, i.name, i.exchange, i.currency, i.asset_class, i.tradable,
                """ + PRICE_COLUMNS + """
                FROM instruments i
                LEFT JOIN market_quote_snapshots s ON s.symbol = i.ticker
                WHERE UPPER(i.ticker) LIKE :contains
                   OR UPPER(i.name) LIKE :prefix
                   OR UPPER(i.name) LIKE :wordPrefix
                   OR UPPER(i.exchange) = :exact
                   OR UPPER(i.asset_class) = :exact
                ORDER BY i.ticker
                LIMIT :limit
                """)
            .param("contains", "%" + escapeLike(needle) + "%")
            .param("prefix", escapeLike(needle) + "%")
            .param("wordPrefix", "% " + escapeLike(needle) + "%")
            .param("exact", needle)
            .param("limit", limit)
            .query(InstrumentRow.class)
            .list();
    }

    /**
     * The FX rate used by the most recent filled order that converted {@code fromCurrency} into
     * {@code toCurrency}. Empty when no such order has settled yet.
     */
    public Optional<FxRow> latestSettledFxRate(String fromCurrency, String toCurrency) {
        return jdbc.sql("""
                SELECT o.fx_rate AS rate, o.executed_on AS as_of
                FROM orders o
                JOIN instruments i ON i.instrument_id = o.instrument_id
                WHERE o.status = 'FILLED'
                  AND o.fx_rate IS NOT NULL
                  AND i.currency = :fromCurrency
                  AND o.settlement_currency = :toCurrency
                ORDER BY o.executed_on DESC
                LIMIT 1
                """)
            .param("fromCurrency", fromCurrency)
            .param("toCurrency", toCurrency)
            .query(FxRow.class)
            .optional();
    }

    public List<OrderRow> findRecentOrders(long accountId, int limit) {
        return jdbc.sql("""
                SELECT i.ticker AS symbol, o.side, o.quantity, o.price AS limit_price, o.executed_price, o.status,
                       i.currency AS instrument_currency, o.settlement_amount, o.settlement_currency,
                       o.rejection_reason, o.created_at AS created_on
                FROM orders o
                JOIN portfolio p ON p.portfolio_id = o.portfolio_id
                JOIN instruments i ON i.instrument_id = o.instrument_id
                WHERE p.account_id = :accountId
                ORDER BY o.created_at DESC
                LIMIT :limit
                """)
            .param("accountId", accountId)
            .param("limit", limit)
            .query(OrderRow.class)
            .list();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
