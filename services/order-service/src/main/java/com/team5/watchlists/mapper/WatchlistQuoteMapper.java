package com.team5.watchlists.mapper;

import com.team5.watchlists.domain.LastQuote;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WatchlistQuoteMapper {

    // 1 when this is the first price stored for the symbol, 0 when it already had one
    int insertIfAbsent(
        @Param("symbol") String symbol,
        @Param("lastPrice") BigDecimal lastPrice,
        @Param("quotedAt") OffsetDateTime quotedAt
    );

    // the stored price, row-locked until the transaction ends
    LastQuote lockLastQuote(@Param("symbol") String symbol);

    int update(
        @Param("symbol") String symbol,
        @Param("lastPrice") BigDecimal lastPrice,
        @Param("quotedAt") OffsetDateTime quotedAt
    );
}
