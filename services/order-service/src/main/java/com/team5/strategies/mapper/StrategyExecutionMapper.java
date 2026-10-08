package com.team5.strategies.mapper;

import com.team5.strategies.domain.ExecutionRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StrategyExecutionMapper {

    boolean exists(@Param("strategyId") long strategyId, @Param("quoteEventId") UUID quoteEventId);

    int insert(ExecutionRow execution);

    List<ExecutionRow> findByStrategy(@Param("strategyId") long strategyId, @Param("limit") int limit);
}
