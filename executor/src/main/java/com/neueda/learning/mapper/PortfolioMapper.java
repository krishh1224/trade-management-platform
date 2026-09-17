package com.neueda.learning.mapper;

import com.neueda.learning.model.PortfolioRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for Portfolio persistence.
 */
@Mapper
public interface PortfolioMapper {

    /**
     * Load a portfolio by its ID.
     */
    @Select("SELECT portfolio_id, name, account_id, status, created_at, updated_at " +
            "FROM portfolio WHERE portfolio_id = #{portfolioId}")
    PortfolioRecord findById(Long portfolioId);
}

