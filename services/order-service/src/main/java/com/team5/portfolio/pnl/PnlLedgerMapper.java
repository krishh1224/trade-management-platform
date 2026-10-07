package com.team5.portfolio.pnl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * The only mapper in the Portfolio module that writes, and it writes only {@code realised_pnl_ledger},
 * the table the module owns. PnlLedgerMapperIntegrationTest fails the build if its XML touches any
 * other table.
 */
@Mapper
public interface PnlLedgerMapper {

    /** Books a sale unless it is already booked. Returns 1 when inserted, 0 when the order was already there. */
    int insertIfAbsent(@Param("entry") LedgerEntry entry);

    List<Long> findBookedOrderIds(@Param("accountId") long accountId);

    /** Realised profit and loss in the base currency, sold in [fromInclusive, toExclusive); either bound may be null. */
    BigDecimal sumRealised(
        @Param("accountId") long accountId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
    );

    List<SymbolRealised> sumRealisedBySymbol(
        @Param("accountId") long accountId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
    );
}
