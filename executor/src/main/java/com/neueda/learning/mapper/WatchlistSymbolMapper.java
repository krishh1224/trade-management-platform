package com.neueda.learning.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WatchlistSymbolMapper {

    /**
     * Get all distinct symbols that appear in any watchlist.
     */
    @Select("SELECT DISTINCT i.ticker " +
            "FROM watchlists w " +
            "JOIN instruments i ON w.instrument_id = i.instrument_id " +
            "ORDER BY i.ticker")
    List<String> findAllWatchlistSymbols();
}
