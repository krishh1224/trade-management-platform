package com.team5.mapper;

import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountQueryMapper {
    // @Param("accountId") - MyBatis param name (that sql query will use in xml file)
    //  #{accountId} - above is mapped to this in xml file
    // Long accountId - Java variable that will be passed to the query
    //a.acc_id - Database column
    AccountResponse getAccount(@Param("accountId") Long accountId);

    BalanceResponse getBalance(@Param("accountId") Long accountId);

    List<PositionResponse> getPositions(@Param("accountId") Long accountId);

    List<OrderHistoryEntry> getOrders(
        @Param("accountId") Long accountId,
        @Param("status") String status,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );

    Map<String, Object> findAccountForOrder(@Param("accountId") Long accountId);

    // total of BUY orders still in NEW, this cash can't be withdrawn
    BigDecimal sumHeldForNewBuyOrders(@Param("accountId") Long accountId);

    int updateBalanceWithVersion(
        @Param("accountId") Long accountId,
        @Param("newBalance") BigDecimal newBalance,
        @Param("expectedVersion") Long expectedVersion,
        @Param("updatedAt") OffsetDateTime updatedAt
    );
}
