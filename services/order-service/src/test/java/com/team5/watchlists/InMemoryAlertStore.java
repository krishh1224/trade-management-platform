package com.team5.watchlists;

import com.team5.notifications.api.AlertDirection;
import com.team5.watchlists.domain.LastQuote;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.domain.PriceAlertStatus;
import com.team5.watchlists.dto.PriceAlertResponse;
import com.team5.watchlists.mapper.PriceAlertMapper;
import com.team5.watchlists.mapper.WatchlistQuoteMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

// Stand-in for price_alerts and watchlist_quotes that behaves like PriceAlertMapper.xml and
// WatchlistQuoteMapper.xml, including the conditional ACTIVE -> TRIGGERED update and
// INSERT ... ON CONFLICT DO NOTHING. Counts the per-quote lookups so tests can see the work done.
public class InMemoryAlertStore {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, PriceAlertRow> alerts = new LinkedHashMap<>();
    private final Map<String, LastQuote> quotes = new HashMap<>();
    private int symbolLookups;

    public final PriceAlertMapper alertMapper = new Alerts();
    public final WatchlistQuoteMapper quoteMapper = new Quotes();

    public PriceAlertRow addActive(long accountId, String symbol, AlertDirection direction, String threshold) {
        PriceAlertRow row = new PriceAlertRow();
        row.setAccountId(accountId);
        row.setWatchlistId(7000L + accountId);
        row.setSymbol(symbol);
        row.setDirection(direction);
        row.setThreshold(new BigDecimal(threshold));
        row.setStatus(PriceAlertStatus.ACTIVE);
        row.setDeliveryId(UUID.randomUUID());
        row.setCreatedAt(OffsetDateTime.parse("2026-10-07T09:00:00Z"));
        alertMapper.insert(row);
        return row;
    }

    public PriceAlertRow alert(long alertId) {
        return alerts.get(alertId);
    }

    public LastQuote quote(String symbol) {
        return quotes.get(symbol);
    }

    public int symbolLookups() {
        return symbolLookups;
    }

    private class Alerts implements PriceAlertMapper {

        @Override
        public Integer lockAccountAlerts(long accountId) {
            return 1;
        }

        @Override
        public int countActiveByAccount(long accountId) {
            return (int) alerts.values().stream()
                .filter(a -> a.getAccountId() == accountId && a.getStatus() == PriceAlertStatus.ACTIVE)
                .count();
        }

        @Override
        public boolean existsActive(long accountId, String symbol, AlertDirection direction, BigDecimal threshold) {
            return alerts.values().stream().anyMatch(a -> a.getAccountId() == accountId
                && a.getSymbol().equals(symbol)
                && a.getDirection() == direction
                && a.getThreshold().compareTo(threshold) == 0
                && a.getStatus() == PriceAlertStatus.ACTIVE);
        }

        @Override
        public int insert(PriceAlertRow alert) {
            alert.setAlertId(ids.getAndIncrement());
            alerts.put(alert.getAlertId(), alert);
            return 1;
        }

        @Override
        public List<PriceAlertResponse> findByAccount(long accountId) {
            return alerts.values().stream()
                .filter(a -> a.getAccountId() == accountId)
                .sorted(Comparator.comparing(PriceAlertRow::getAlertId).reversed())
                .map(this::toResponse)
                .toList();
        }

        @Override
        public PriceAlertResponse findForAccount(long alertId, long accountId) {
            PriceAlertRow row = alerts.get(alertId);
            return row != null && row.getAccountId() == accountId ? toResponse(row) : null;
        }

        @Override
        public Long findOwner(long alertId) {
            PriceAlertRow row = alerts.get(alertId);
            return row == null ? null : row.getAccountId();
        }

        @Override
        public int deleteForAccount(long alertId, long accountId) {
            PriceAlertRow row = alerts.get(alertId);
            if (row == null || row.getAccountId() != accountId) {
                return 0;
            }
            alerts.remove(alertId);
            return 1;
        }

        @Override
        public List<PriceAlertRow> findActiveBySymbolBetween(String symbol, BigDecimal low, BigDecimal high) {
            symbolLookups++;
            List<PriceAlertRow> found = new ArrayList<>();
            for (PriceAlertRow row : alerts.values()) {
                if (row.getSymbol().equals(symbol) && row.getStatus() == PriceAlertStatus.ACTIVE
                    && row.getThreshold().compareTo(low) >= 0 && row.getThreshold().compareTo(high) <= 0) {
                    found.add(row);
                }
            }
            return found;
        }

        @Override
        public int markTriggered(long alertId, BigDecimal triggerPrice, OffsetDateTime triggeredAt) {
            PriceAlertRow row = alerts.get(alertId);
            if (row == null || row.getStatus() != PriceAlertStatus.ACTIVE) {
                return 0;
            }
            row.setStatus(PriceAlertStatus.TRIGGERED);
            row.setTriggerPrice(triggerPrice);
            row.setTriggeredAt(triggeredAt);
            return 1;
        }

        private PriceAlertResponse toResponse(PriceAlertRow row) {
            LastQuote quote = quotes.get(row.getSymbol());
            return new PriceAlertResponse(row.getAlertId(), row.getWatchlistId(), row.getSymbol(), row.getSymbol(),
                row.getDirection(), row.getThreshold(), row.getStatus(), row.getCreatedAt(), row.getTriggeredAt(),
                row.getTriggerPrice(), quote == null ? null : quote.lastPrice(), "INR");
        }
    }

    private class Quotes implements WatchlistQuoteMapper {

        @Override
        public int insertIfAbsent(String symbol, BigDecimal lastPrice, OffsetDateTime quotedAt) {
            if (quotes.containsKey(symbol)) {
                return 0;
            }
            quotes.put(symbol, new LastQuote(lastPrice, quotedAt));
            return 1;
        }

        @Override
        public LastQuote lockLastQuote(String symbol) {
            return quotes.get(symbol);
        }

        @Override
        public int update(String symbol, BigDecimal lastPrice, OffsetDateTime quotedAt) {
            quotes.put(symbol, new LastQuote(lastPrice, quotedAt));
            return 1;
        }
    }
}
