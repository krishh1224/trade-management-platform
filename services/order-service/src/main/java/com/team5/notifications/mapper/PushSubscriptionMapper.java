package com.team5.notifications.mapper;

import com.team5.notifications.domain.PushSubscription;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PushSubscriptionMapper {

    // a browser belongs to the last account that enabled push on it, so the endpoint is the key
    int upsert(
        @Param("accountId") long accountId,
        @Param("endpoint") String endpoint,
        @Param("p256dh") String p256dh,
        @Param("auth") String auth
    );

    List<PushSubscription> findByAccount(@Param("accountId") long accountId);

    // account_id is in the WHERE clause so one account can never remove another's browser
    int deleteForAccount(@Param("accountId") long accountId, @Param("endpoint") String endpoint);

    // the push service said this browser is gone (404/410), or the endpoint failed the allowlist
    int deleteById(@Param("subscriptionId") long subscriptionId);
}
