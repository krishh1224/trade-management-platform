package com.team5.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// orders.order_id is derived from the order's UUID (see V6_add_external_order_id.sql).
// Shared so every producer of an order id, including trade-events, agrees with the database.
public final class OrderIdentifiers {

    private OrderIdentifiers() {
    }

    public static long persistentId(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return mixed == Long.MIN_VALUE ? 0L : Math.abs(mixed);
    }

    // The one eventId a cancellation of this order ever gets. MUST stay identical to the executor's
    // TradeEventPublisher.cancelledEventId: the trade-api records the cancel notification at once and
    // the executor announces the same cancel on trade-events later; the shared eventId makes the
    // second arrival a no-op (notifications.event_id is unique). Both sides pin the same test value.
    public static UUID cancelledEventId(long persistentOrderId) {
        return UUID.nameUUIDFromBytes(("ORDER_CANCELLED:" + persistentOrderId).getBytes(StandardCharsets.UTF_8));
    }
}
