package com.team5.mapper;

import com.team5.dto.response.WatchlistItemResponse;
import com.team5.dto.response.WatchlistGroupResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WatchlistMapper {

    boolean existsInWatchlist(
        @Param("watchlistGroupId") Long watchlistGroupId,
        @Param("instrumentId") Long instrumentId
    );

    int insertWatchlistItem(
        @Param("watchlistId") Long watchlistId,
        @Param("accountId") Long accountId,
        @Param("watchlistGroupId") Long watchlistGroupId,
        @Param("instrumentId") Long instrumentId,
        @Param("addedAt") OffsetDateTime addedAt
    );

    List<WatchlistItemResponse> findWatchlistByAccountId(
        @Param("accountId") Long accountId,
        @Param("watchlistGroupId") Long watchlistGroupId
    );

    List<WatchlistGroupResponse> findWatchlistGroups(@Param("accountId") Long accountId);

    Long findWatchlistGroupById(
        @Param("accountId") Long accountId,
        @Param("watchlistGroupId") Long watchlistGroupId
    );

    Long findWatchlistGroupByName(
        @Param("accountId") Long accountId,
        @Param("name") String name
    );

    Long findDefaultWatchlistGroupId(@Param("accountId") Long accountId);

    int insertWatchlistGroup(
        @Param("watchlistGroupId") Long watchlistGroupId,
        @Param("accountId") Long accountId,
        @Param("name") String name,
        @Param("createdAt") OffsetDateTime createdAt
    );

    Long findAccountIdByWatchlistId(
        @Param("accountId") Long accountId,
        @Param("watchlistId") Long watchlistId
    );

    int deleteWatchlistItem(
        @Param("accountId") Long accountId,
        @Param("watchlistId") Long watchlistId
    );
}
