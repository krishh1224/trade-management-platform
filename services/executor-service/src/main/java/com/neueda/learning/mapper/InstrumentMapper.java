package com.neueda.learning.mapper;

import com.neueda.learning.model.InstrumentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for Instrument persistence.
 */
@Mapper
public interface InstrumentMapper {

    /**
     * Load an instrument by its ID.
     */
    @Select("SELECT instrument_id, ticker, name, asset_class, currency, tradable " +
            "FROM instruments WHERE instrument_id = #{instrumentId}")
    InstrumentRecord findById(Long instrumentId);
}

