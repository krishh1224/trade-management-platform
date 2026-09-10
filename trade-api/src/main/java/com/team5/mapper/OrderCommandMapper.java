package com.team5.mapper;

import com.team5.dto.response.OrderResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderCommandMapper {

    int insertOrder(
        @Param("orderId") String orderId,
        @Param("accountId") Long accountId,
        @Param("symbol") String symbol,
        @Param("side") String side,
        @Param("quantity") Integer quantity,
        @Param("price") BigDecimal price,
        @Param("status") String status,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("createdOn") OffsetDateTime createdOn,
        @Param("updatedOn") OffsetDateTime updatedOn
    );

    int updateOrderStatus(
        @Param("orderId") String orderId,
        @Param("status") String status,
        @Param("updatedOn") OffsetDateTime updatedOn
    );

    OrderResponse findOrderByOrderId(@Param("orderId") String orderId);
}
