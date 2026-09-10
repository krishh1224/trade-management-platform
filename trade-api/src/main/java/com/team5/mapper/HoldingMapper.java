package com.team5.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HoldingMapper {

    Map<String, Object> findHolding(
        @Param("portfolioId") Long portfolioId,
        @Param("instrumentId") Long instrumentId
    );

    int updateHolding(
        @Param("portfolioHoldingId") Long portfolioHoldingId,
        @Param("quantity") Long quantity,
        @Param("avgCost") BigDecimal avgCost,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int insertHolding(
        @Param("portfolioHoldingId") Long portfolioHoldingId,
        @Param("portfolioId") Long portfolioId,
        @Param("instrumentId") Long instrumentId,
        @Param("quantity") Long quantity,
        @Param("avgCost") BigDecimal avgCost,
        @Param("createdAt") OffsetDateTime createdAt,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int deleteHolding(@Param("portfolioHoldingId") Long portfolioHoldingId);
}