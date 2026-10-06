package com.neueda.learning.mapper;

import com.neueda.learning.model.OrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
            "idempotency_key, created_at, updated_at, expires_at, executed_price, executed_on, rejection_reason " +
            "FROM orders WHERE order_id = #{orderId}")
    OrderRecord findById(Long orderId);

    /**
     * Conditional claim: update order status only if it is currently NEW.
     * This is the first write in the settlement transaction and guards against duplicate processing.
     *
     * Returns the number of rows affected (0 = already processed, 1 = successfully claimed).
     *
     * @param orderId the order ID to update
     * @param newStatus the new status (e.g., FILLED or REJECTED)
     * @param executedPrice the execution price (null for REJECTED)
     * @param executedOn the execution timestamp
     * @param rejectionReason the rejection reason (null for FILLED)
     * @return affected rows count (0 or 1)
     */
    @Update("UPDATE orders " +
            "SET status = #{newStatus}, " +
            "    executed_price = #{executedPrice}, " +
            "    executed_on = #{executedOn}, " +
            "    rejection_reason = #{rejectionReason}, " +
            "    fx_rate = #{fxRate}, " +
            "    settlement_currency = #{settlementCurrency}, " +
            "    settlement_amount = #{settlementAmount}, " +
            "    updated_at = CURRENT_TIMESTAMP " +
            "WHERE order_id = #{orderId} " +
            "  AND status = 'NEW'")
    int claimOrderIfNew(Long orderId, String newStatus, BigDecimal executedPrice,
                        LocalDateTime executedOn, String rejectionReason,
                        BigDecimal fxRate, String settlementCurrency, BigDecimal settlementAmount);
}
