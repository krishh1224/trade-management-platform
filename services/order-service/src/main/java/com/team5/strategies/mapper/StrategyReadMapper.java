package com.team5.strategies.mapper;

import com.team5.strategies.domain.PositionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// Reads of other modules' tables, all SELECT. The strategies module never writes instruments,
// portfolio_holdings or orders: an order only ever comes into being through POST /api/v1/orders.
@Mapper
public interface StrategyReadMapper {

    // the ticker, when the instrument exists and is tradable
    String findTradableSymbol(@Param("symbol") String symbol);

    // what the account holds of the symbol, and what its NEW orders will add or take away
    PositionSnapshot findPosition(@Param("accountId") long accountId, @Param("symbol") String symbol);

    // the order the Trade API stored under this idempotency key, if any
    String findOrderIdByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
