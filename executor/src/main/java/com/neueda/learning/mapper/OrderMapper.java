package com.neueda.learning.mapper;

import com.neueda.learning.model.OrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for Order persistence.
 * Loads orders from the shared PostgreSQL database.
 */
@Mapper
public interface OrderMapper {

    /**
     * Load an order by its ID.
     * Returns the order record including all fields needed for execution.
     */
    @Select("SELECT order_id, portfolio_id, instrument_id, quantity, price, status, side, " +
            "idempotency_key, created_at, updated_at, expires_at " +
            "FROM orders WHERE order_id = #{orderId}")
    OrderRecord findById(Long orderId);
}
