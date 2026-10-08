package com.team5.notifications.mapper;

import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.preferences.AlertChannel;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    // INSERT ... ON CONFLICT (event_id) DO NOTHING: 1 = recorded, 0 = this event was already seen
    int insertIfAbsent(Notification notification);

    // QUEUED first, then HELD, oldest first
    List<Notification> findPending(@Param("limit") int limit);

    int updateDeliveryState(
        @Param("notificationId") long notificationId,
        @Param("status") NotificationStatus status,
        @Param("channel") AlertChannel channel,
        @Param("lastError") String lastError,
        @Param("sentAt") OffsetDateTime sentAt
    );

    List<Notification> findByAccount(
        @Param("accountId") long accountId,
        @Param("status") NotificationStatus status,
        @Param("limit") int limit
    );

    // account_id is part of the WHERE clause so a row of another account is never returned
    Notification findByIdForAccount(
        @Param("notificationId") long notificationId,
        @Param("accountId") long accountId
    );

    // fills in symbol/side/quantity for an executor rejection, which carries only the order id.
    // Scoped to the event's account so an event can never pull in another account's order.
    StoredOrder findOrderForAccount(
        @Param("orderId") long orderId,
        @Param("accountId") long accountId
    );

    // where the EMAIL channel delivers: always the address on file for the notification's own
    // account, never one supplied by a caller. null when the account has no client row.
    String findEmailForAccount(@Param("accountId") long accountId);

    // only sets read_at the first time, so marking read twice keeps the original timestamp
    int markRead(
        @Param("notificationId") long notificationId,
        @Param("accountId") long accountId,
        @Param("readAt") OffsetDateTime readAt
    );
}
