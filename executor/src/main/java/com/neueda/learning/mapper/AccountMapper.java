package com.neueda.learning.mapper;

import com.neueda.learning.model.AccountRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for Account persistence.
 */
@Mapper
public interface AccountMapper {

    /**
     * Load an account by its ID.
     */
    @Select("SELECT acc_id, client_id, account_number, risk_profile, base_currency, " +
            "balance, status, created_at, updated_at " +
            "FROM accounts WHERE acc_id = #{accountId}")
    AccountRecord findById(Long accountId);
}

