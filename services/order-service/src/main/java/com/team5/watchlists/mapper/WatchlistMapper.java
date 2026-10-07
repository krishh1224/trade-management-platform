package com.team5.watchlists.mapper;

import com.team5.watchlists.domain.WatchlistEntry;
import com.team5.watchlists.domain.WatchlistGroupRef;
import com.team5.watchlists.domain.WatchlistInstrument;
import com.team5.watchlists.dto.WatchlistGroupResponse;
import com.team5.watchlists.dto.WatchlistItemResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WatchlistMapper {

    // read-only lookup on the shared instruments reference table
    WatchlistInstrument findInstrumentByTicker(@Param("ticker") String ticker);

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

    // the owning account of a group, whoever asks; null when the group does not exist
    Long findWatchlistGroupOwner(@Param("watchlistGroupId") Long watchlistGroupId);

    // the group with its owner and name, whoever asks; null when the group does not exist
    WatchlistGroupRef findWatchlistGroup(@Param("watchlistGroupId") Long watchlistGroupId);

    // the group's entries, and their price alerts, go with it (ON DELETE CASCADE)
    int deleteWatchlistGroup(
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

    // the entry with its owner, whoever asks; null when the entry does not exist
    WatchlistEntry findWatchlistEntry(@Param("watchlistId") Long watchlistId);

    int deleteWatchlistItem(
        @Param("accountId") Long accountId,
        @Param("watchlistId") Long watchlistId
    );
}
