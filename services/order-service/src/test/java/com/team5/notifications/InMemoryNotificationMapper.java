package com.team5.notifications;

import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.notifications.mapper.StoredOrder;
import com.team5.preferences.AlertChannel;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

// Stand-in for the notifications tables that behaves like the SQL in NotificationMapper.xml,
// including the unique constraint on event_id that makes a replay a no-op.
public class InMemoryNotificationMapper implements NotificationMapper {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, Notification> rows = new LinkedHashMap<>();
    private final Map<UUID, Long> byEventId = new HashMap<>();
    private final Map<String, StoredOrder> orders = new HashMap<>();
    private final Map<Long, String> emails = new HashMap<>();

    public void storeOrder(long orderId, long accountId, StoredOrder order) {
        orders.put(orderId + ":" + accountId, order);
    }

    public void storeEmail(long accountId, String email) {
        emails.put(accountId, email);
    }

    public List<Notification> rows() {
        return new ArrayList<>(rows.values());
    }

    @Override
    public int insertIfAbsent(Notification notification) {
        if (byEventId.containsKey(notification.getEventId())) {
            return 0;
        }
        long id = ids.getAndIncrement();
        Notification copy = copy(notification);
        copy.setNotificationId(id);
        rows.put(id, copy);
        byEventId.put(copy.getEventId(), id);
        notification.setNotificationId(id);
        return 1;
    }

    @Override
    public List<Notification> findPending(int limit) {
        return rows.values().stream()
            .filter(n -> n.getStatus() == NotificationStatus.QUEUED || n.getStatus() == NotificationStatus.HELD)
            .sorted(Comparator.comparing((Notification n) -> n.getStatus() == NotificationStatus.QUEUED ? 0 : 1)
                .thenComparing(Notification::getNotificationId))
            .limit(limit)
            .map(this::copy)
            .toList();
    }

    @Override
    public int updateDeliveryState(long notificationId, NotificationStatus status, AlertChannel channel,
                                   String lastError, OffsetDateTime sentAt) {
        Notification row = rows.get(notificationId);
        if (row == null) {
            return 0;
        }
        row.setStatus(status);
        row.setChannel(channel);
        row.setLastError(lastError);
        row.setSentAt(sentAt);
        return 1;
    }

    @Override
    public List<Notification> findByAccount(long accountId, NotificationStatus status, int limit) {
        return rows.values().stream()
            .filter(n -> n.getAccountId() == accountId)
            .filter(n -> status == null || n.getStatus() == status)
            .sorted(Comparator.comparing(Notification::getNotificationId).reversed())
            .limit(limit)
            .map(this::copy)
            .toList();
    }

    @Override
    public Notification findByIdForAccount(long notificationId, long accountId) {
        Notification row = rows.get(notificationId);
        return row != null && row.getAccountId() == accountId ? copy(row) : null;
    }

    @Override
    public StoredOrder findOrderForAccount(long orderId, long accountId) {
        return orders.get(orderId + ":" + accountId);
    }

    @Override
    public String findEmailForAccount(long accountId) {
        return emails.get(accountId);
    }

    @Override
    public int markRead(long notificationId, long accountId, OffsetDateTime readAt) {
        Notification row = rows.get(notificationId);
        if (row == null || row.getAccountId() != accountId || row.getReadAt() != null) {
            return 0;
        }
        row.setReadAt(readAt);
        return 1;
    }

    private Notification copy(Notification source) {
        Notification copy = new Notification();
        copy.setNotificationId(source.getNotificationId());
        copy.setEventId(source.getEventId());
        copy.setAccountId(source.getAccountId());
        copy.setType(source.getType());
        copy.setOrderId(source.getOrderId());
        copy.setMessage(source.getMessage());
        copy.setChannel(source.getChannel());
        copy.setStatus(source.getStatus());
        copy.setLastError(source.getLastError());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setSentAt(source.getSentAt());
        copy.setReadAt(source.getReadAt());
        return copy;
    }
}
