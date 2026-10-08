package com.team5.strategies.mapper;

import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StrategyMapper {

    int insert(StrategyRow strategy);

    StrategyRow findForAccount(@Param("strategyId") long strategyId, @Param("accountId") long accountId);

    Long findOwner(@Param("strategyId") long strategyId);

    List<StrategyRow> findByAccount(@Param("accountId") long accountId);

    int countByAccount(@Param("accountId") long accountId);

    // serialises creates for one account until the transaction ends, so the cap cannot be raced
    Integer lockAccountStrategies(@Param("accountId") long accountId);

    // the strategies a quote for this symbol is evaluated against
    List<StrategyRow> findEnabledBySymbol(@Param("symbol") String symbol);

    // the strategy, row-locked until the transaction ends: every order decision is taken under this
    // lock, and a disable or delete waits for it
    StrategyRow lockById(@Param("strategyId") long strategyId);

    // the customer's switch; also clears the failure count when enabling
    int updateStatus(
        @Param("strategyId") long strategyId,
        @Param("accountId") long accountId,
        @Param("status") StrategyStatus status,
        @Param("statusReason") String statusReason,
        @Param("resetFailures") boolean resetFailures,
        @Param("now") OffsetDateTime now
    );

    // an order the Trade API accepted: count it, add its value to spent, clear the failure run
    int recordPlaced(
        @Param("strategyId") long strategyId,
        @Param("spend") BigDecimal spend,
        @Param("now") OffsetDateTime now
    );

    // an order that failed: extend the failure run, add any spend that may have happened, and halt
    // the strategy when the run reaches its limit
    int recordFailure(
        @Param("strategyId") long strategyId,
        @Param("spend") BigDecimal spend,
        @Param("haltReason") String haltReason,
        @Param("now") OffsetDateTime now
    );

    // a rule fired but a control stopped the order
    int recordTriggered(@Param("strategyId") long strategyId, @Param("now") OffsetDateTime now);

    int delete(@Param("strategyId") long strategyId, @Param("accountId") long accountId);
}
