package com.team5.portfolio.pnl;

import com.team5.dto.response.PositionResponse;
import com.team5.portfolio.pnl.CostBasisReplay.Fill;
import com.team5.portfolio.pnl.CostBasisReplay.Holding;
import com.team5.portfolio.read.FilledOrder;
import com.team5.portfolio.read.PortfolioReadMapper;
import com.team5.service.AccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Realised profit and loss, accumulated from sales rather than recomputed from prices.
 *
 * <p>Every filled SELL is booked once, in {@code realised_pnl_ledger}, as
 * {@code (fill price - average cost at sale) * quantity}, converted at the FX rate the order filled at.
 * A booked sale is never revisited: its figure does not move with today's price, and the order id is the
 * ledger's key, so seeing the same sale again (a second request, a replay, two requests racing) books
 * nothing and counts nothing twice.
 *
 * <p>Sales are picked up whenever the ledger is read: anything filled since the last read is booked first.
 * The trading tables are only read, never written.
 */
@Service
public class RealisedPnlLedger implements RealisedPnlProvider {

    private static final Logger log = LoggerFactory.getLogger(RealisedPnlLedger.class);

    private final PortfolioReadMapper readMapper;
    private final PnlLedgerMapper ledgerMapper;
    private final AccountService accountService;

    public RealisedPnlLedger(PortfolioReadMapper readMapper, PnlLedgerMapper ledgerMapper, AccountService accountService) {
        this.readMapper = readMapper;
        this.ledgerMapper = ledgerMapper;
        this.accountService = accountService;
    }

    @Override
    public BigDecimal lifetimeRealised(long accountId, String baseCurrency) {
        return realised(accountId, baseCurrency, null, null);
    }

    /** Realised profit and loss for sales made on or after {@code from} and on or before {@code to}; either may be null. */
    public BigDecimal realised(long accountId, String baseCurrency, LocalDate from, LocalDate to) {
        book(accountId, baseCurrency);
        BigDecimal total = ledgerMapper.sumRealised(accountId, start(from), endExclusive(to));
        return (total == null ? BigDecimal.ZERO : total).setScale(2, RoundingMode.HALF_UP);
    }

    /** The same figure per instrument, ordered by symbol. */
    public Map<String, BigDecimal> realisedBySymbol(long accountId, String baseCurrency, LocalDate from, LocalDate to) {
        book(accountId, baseCurrency);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (SymbolRealised row : ledgerMapper.sumRealisedBySymbol(accountId, start(from), endExclusive(to))) {
            result.put(row.symbol(), row.realisedPnl().setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    /** Books every sale not yet in the ledger. Returns how many were booked now. */
    public int book(long accountId, String baseCurrency) {
        List<FilledOrder> filled = readMapper.findFilledOrders(accountId);
        Set<Long> booked = new HashSet<>(ledgerMapper.findBookedOrderIds(accountId));
        if (filled.stream().noneMatch(order -> order.isSell() && !booked.contains(order.orderId()))) {
            return 0;
        }

        // The account is already authorised by whoever asked, so it stands in for the caller here.
        Map<String, Holding> holdings = accountService.getPositions(accountId, accountId).stream()
            .filter(position -> position.quantity() != null && position.quantity() > 0)
            .collect(Collectors.toMap(PositionResponse::symbol,
                position -> new Holding(position.quantity(), position.averageCost()), (a, b) -> a));

        int bookedNow = 0;
        for (Map.Entry<String, List<FilledOrder>> instrument : bySymbol(filled).entrySet()) {
            List<FilledOrder> orders = instrument.getValue();
            Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
                orders.stream().map(order -> new Fill(order.orderId(), order.isSell(), order.quantity(), order.fillPrice())).toList(),
                holdings.get(instrument.getKey()));

            for (FilledOrder sale : orders) {
                if (!sale.isSell() || booked.contains(sale.orderId())) {
                    continue;
                }
                bookedNow += bookSale(accountId, baseCurrency, sale, costs.get(sale.orderId()));
            }
        }
        return bookedNow;
    }

    private int bookSale(long accountId, String baseCurrency, FilledOrder sale, BigDecimal averageCost) {
        if (averageCost == null) {
            log.warn("Sale {} of {} cannot be booked: the order history does not explain the cost of the units sold",
                sale.orderId(), sale.symbol());
            return 0;
        }
        BigDecimal fxRate = sale.fxRate() != null ? sale.fxRate()
            : sale.instrumentCurrency().equals(baseCurrency) ? BigDecimal.ONE : null;
        if (fxRate == null || fxRate.signum() <= 0) {
            log.warn("Sale {} of {} cannot be booked: no FX rate from {} to {} was recorded",
                sale.orderId(), sale.symbol(), sale.instrumentCurrency(), baseCurrency);
            return 0;
        }

        BigDecimal realised = sale.fillPrice().subtract(averageCost)
            .multiply(BigDecimal.valueOf(sale.quantity())).setScale(4, RoundingMode.HALF_UP);
        BigDecimal realisedBase = realised.multiply(fxRate).setScale(2, RoundingMode.HALF_UP);

        return ledgerMapper.insertIfAbsent(new LedgerEntry(sale.orderId(), accountId, sale.symbol(),
            sale.instrumentCurrency(), baseCurrency, sale.quantity(), sale.fillPrice(), averageCost, fxRate,
            realised, realisedBase, sale.executedAt()));
    }

    private static Map<String, List<FilledOrder>> bySymbol(List<FilledOrder> filled) {
        return filled.stream().collect(Collectors.groupingBy(FilledOrder::symbol, LinkedHashMap::new,
            Collectors.toList()));
    }

    private static LocalDateTime start(LocalDate from) {
        return from == null ? null : from.atStartOfDay();
    }

    private static LocalDateTime endExclusive(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay();
    }
}
