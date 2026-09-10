package com.team5.mapper;

import com.team5.dto.response.OrderResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {

    Long findPortfolioIdByAccountId(@Param("accountId") Long accountId);

    Map<String, Object> findInstrumentByTicker(@Param("ticker") String ticker);

    int insertOrder(
        @Param("orderId") Long orderId,
        @Param("portfolioId") Long portfolioId,
        @Param("instrumentId") Long instrumentId,
        @Param("side") String side,
        @Param("quantity") Long quantity,
        @Param("price") BigDecimal price,
        @Param("status") String status,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("createdAt") OffsetDateTime createdAt,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int updateOrderStatusConditionally(
        @Param("orderId") Long orderId,
        @Param("expectedCurrentStatus") String expectedCurrentStatus,
        @Param("newStatus") String newStatus,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    OrderResponse findOrderById(
        @Param("orderId") Long orderId,
        @Param("externalOrderId") String externalOrderId
    );
}