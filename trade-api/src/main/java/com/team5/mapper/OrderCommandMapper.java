package com.team5.mapper;

import com.team5.dto.response.OrderResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderCommandMapper {

    OrderResponse placeOrder(
        @Param("orderId") String orderId,
        @Param("symbol") String symbol,
        @Param("side") String side,
        @Param("quantity") Integer quantity,
        @Param("price") java.math.BigDecimal price
    );

    OrderResponse cancelOrder(@Param("orderId") String orderId);
}