package com.neueda.learning.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MarketQuoteSnapshotMapper {

    @Select("SELECT last_price FROM market_quote_snapshots WHERE symbol = #{symbol}")
    BigDecimal findLastPriceBySymbol(@Param("symbol") String symbol);

    @Insert("""
        INSERT INTO market_quote_snapshots (symbol, last_price, change_pct, updated_at)
        VALUES (#{symbol}, #{lastPrice}, #{changePct}, #{updatedAt})
        ON CONFLICT (symbol) DO UPDATE
        SET last_price = EXCLUDED.last_price,
            change_pct = EXCLUDED.change_pct,
            updated_at = EXCLUDED.updated_at
        """)
    int upsertSnapshot(
        @Param("symbol") String symbol,
        @Param("lastPrice") BigDecimal lastPrice,
        @Param("changePct") BigDecimal changePct,
        @Param("updatedAt") OffsetDateTime updatedAt
    );
}
