package com.neueda.learning.mapper;

import com.neueda.learning.model.AccountRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

/**
 * MyBatis mapper for Account persistence.
 * Handles account reads and optimistic locking updates.
 */
@Mapper
public interface AccountMapper {

    /**
     * Load an account by its ID, including version for optimistic locking.
     */
    @Select("SELECT acc_id, client_id, account_number, risk_profile, base_currency, " +
            "balance, status, version, created_at, updated_at " +
            "FROM accounts WHERE acc_id = #{accountId}")
    AccountRecord findById(Long accountId);

    /**
     * Update account balance with optimistic locking on version.
     * Only updates if the current version matches the expectedVersion.
     * Also increments version on successful update.
     *
     * Returns the number of rows affected (0 = version mismatch, 1 = success).
     *
     * @param accountId the account ID
     * @param newBalance the new balance
     * @param expectedVersion the version we expect to find
     * @return affected rows count (0 or 1)
     */
    @Update("UPDATE accounts " +
            "SET balance = #{newBalance}, " +
            "    version = version + 1, " +
            "    updated_at = CURRENT_TIMESTAMP " +
            "WHERE acc_id = #{accountId} " +
            "  AND version = #{expectedVersion}")
    int updateBalanceWithVersionCheck(Long accountId, BigDecimal newBalance, Long expectedVersion);
}

