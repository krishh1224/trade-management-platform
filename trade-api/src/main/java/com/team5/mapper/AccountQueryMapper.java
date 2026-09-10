package com.team5.mapper;

import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountQueryMapper {

    AccountResponse getAccount(@Param("accountId") Long accountId);

    BalanceResponse getBalance(@Param("accountId") Long accountId);

    List<PositionResponse> getPositions(@Param("accountId") Long accountId);

    List<OrderHistoryEntry> getOrders(
        @Param("accountId") Long accountId,
        @Param("status") String status,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );
}
