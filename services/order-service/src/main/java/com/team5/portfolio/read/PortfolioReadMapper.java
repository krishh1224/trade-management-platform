package com.team5.portfolio.read;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Read-only access to tables this module does not own. It shares the connection the order path
 * writes through, so the discipline is in the mapper XML: SELECT statements only, enforced by
 * PortfolioReadMapperReadOnlyTest.
 */
@Mapper
public interface PortfolioReadMapper {

    List<InstrumentCurrency> findCurrencies(@Param("symbols") Collection<String> symbols);

    List<SnapshotPrice> findSnapshots(@Param("symbols") Collection<String> symbols);

    /** Every filled order of the account, oldest first within each instrument. */
    List<FilledOrder> findFilledOrders(@Param("accountId") long accountId);
}
