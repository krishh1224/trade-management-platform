package com.team5.mapper;

import com.team5.dto.response.WatchlistItemResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WatchlistMapper {

    boolean existsInWatchlist(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    int insertWatchlistItem(
        @Param("watchlistId") Long watchlistId,
        @Param("accountId") Long accountId,
        @Param("instrumentId") Long instrumentId,
        @Param("addedAt") OffsetDateTime addedAt
    );

    List<WatchlistItemResponse> findWatchlistByAccountId(@Param("accountId") Long accountId);

    Long findAccountIdByInstrumentId(
        @Param("accountId") Long accountId,
        @Param("instrumentId") Long instrumentId
    );

    int deleteWatchlistItem(
        @Param("accountId") Long accountId,
        @Param("instrumentId") Long instrumentId
    );
}
