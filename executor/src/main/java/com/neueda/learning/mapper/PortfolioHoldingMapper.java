package com.neueda.learning.mapper;

import com.neueda.learning.model.PortfolioHoldingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

/**
 * MyBatis mapper for Portfolio Holding persistence.
 * Handles reading and updating positions (quantities) for settlement.
 */
@Mapper
public interface PortfolioHoldingMapper {

    /**
     * Find a holding by portfolio and instrument.
     * Returns null if no holding exists (e.g., for a new BUY).
     */
    @Select("SELECT portfolio_holding_id, portfolio_id, instrument_id, quantity, avg_cost, " +
            "created_at, updated_at " +
            "FROM portfolio_holdings " +
            "WHERE portfolio_id = #{portfolioId} AND instrument_id = #{instrumentId}")
    PortfolioHoldingRecord findByPortfolioAndInstrument(Long portfolioId, Long instrumentId);

    /**
     * Update the quantity of an existing holding.
     * Called after a BUY or SELL execution.
     *
     * @param portfolioHoldingId the holding ID
     * @param newQuantity the new quantity
     * @return affected rows count
     */
    @Update("UPDATE portfolio_holdings " +
            "SET quantity = #{newQuantity}, " +
            "    updated_at = CURRENT_TIMESTAMP " +
            "WHERE portfolio_holding_id = #{portfolioHoldingId}")
    int updateQuantity(Long portfolioHoldingId, Long newQuantity);

    /**
     * Create a new holding for a portfolio and instrument.
     * Used when a portfolio first buys an instrument.
     *
     * @param portfolioHoldingId the new holding ID
     * @param portfolioId the portfolio ID
     * @param instrumentId the instrument ID
     * @param quantity the initial quantity
     * @param avgCost the average cost per unit
     * @return affected rows count
     */
    @Insert("INSERT INTO portfolio_holdings " +
            "(portfolio_holding_id, portfolio_id, instrument_id, quantity, avg_cost, created_at, updated_at) " +
            "VALUES (#{portfolioHoldingId}, #{portfolioId}, #{instrumentId}, #{quantity}, #{avgCost}, " +
            "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insertHolding(Long portfolioHoldingId, Long portfolioId, Long instrumentId,
                      Long quantity, java.math.BigDecimal avgCost);

    /**
     * Get all distinct tickers from portfolio holdings.
     * Used by market-data poller to determine which symbols to fetch quotes for.
     * Returns distinct instrument tickers that someone currently holds.
     *
     * @return list of ticker symbols (e.g., "INFY.NS", "RELIANCE.NS")
     */
    @Select("SELECT DISTINCT i.ticker " +
            "FROM portfolio_holdings ph " +
            "JOIN instruments i ON ph.instrument_id = i.instrument_id " +
            "WHERE ph.quantity > 0 " +
            "ORDER BY i.ticker")
    List<String> findAllHeldSymbols();
}
