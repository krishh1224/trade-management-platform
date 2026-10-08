package com.neueda.learning.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StrategySymbolMapper {

    /**
     * Get all distinct symbols an ENABLED automated strategy trades (Trade API strategies module, V18).
     * A strategy needs quotes for its symbol whether or not anyone holds or watches it.
     */
    @Select("SELECT DISTINCT symbol " +
            "FROM strategies " +
            "WHERE status = 'ENABLED' " +
            "ORDER BY symbol")
    List<String> findAllStrategySymbols();
}
