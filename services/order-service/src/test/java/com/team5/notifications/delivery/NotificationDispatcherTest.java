package com.team5.notifications.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.notifications.InMemoryNotificationMapper;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.preferences.AlertChannel;
import com.team5.preferences.CustomerPreferencesLookup;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDispatcherTest {

    private static final long ACCOUNT = 101L;

    private InMemoryNotificationMapper mapper;
    private StoredPreferences preferences;
    private RecordingSender email;
    private RecordingSender sms;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryNotificationMapper();
        preferences = new StoredPreferences();
        email = new RecordingSender(AlertChannel.EMAIL);
        sms = new RecordingSender(AlertChannel.SMS);
        ChannelSenderRegistry registry = new ChannelSenderRegistry(List.of(email, sms));
        dispatcher = new NotificationDispatcher(mapper, preferences, registry, new DeliveryLedger(mapper), 50);
    }

    // Execution path 3: the channel comes from Customer Preferences, and changing the
    // preference changes the channel used
    @Test
    void channelComesFromPreferences_andChangingThePreferenceChangesTheChannel() {
        preferences.store(ACCOUNT, AlertChannel.EMAIL);
        long first = queue(ACCOUNT);

        dispatcher.dispatchPending();

        assertEquals(List.of(first), email.sentIds);
        assertTrue(sms.sentIds.isEmpty());
        assertSent(first, AlertChannel.EMAIL);

        // the customer switches to SMS; nothing restarts, nothing is cached
        preferences.store(ACCOUNT, AlertChannel.SMS);
        long second = queue(ACCOUNT);

        dispatcher.dispatchPending();

        assertEquals(List.of(first), email.sentIds, "email must not be used after the switch");
        assertEquals(List.of(second), sms.sentIds);
        assertSent(second, AlertChannel.SMS);
        assertEquals(AlertChannel.EMAIL, row(first).getChannel(), "the first record keeps the channel it went out on");
    }

    @Test
    void preferenceNone_isSkippedAndRecordedAsResolved() {
        preferences.store(ACCOUNT, AlertChannel.NONE);
        long id = queue(ACCOUNT);

        dispatcher.dispatchPending();

        assertTrue(email.sentIds.isEmpty());
        assertTrue(sms.sentIds.isEmpty());
        assertEquals(NotificationStatus.SKIPPED, row(id).getStatus());
        assertEquals(AlertChannel.NONE, row(id).getChannel());
        assertNull(row(id).getSentAt());
        assertNull(row(id).getLastError());
    }

    @Test
    void noPreferenceStored_isHeldThenSentOnceAPreferenceExists() {
        long id = queue(ACCOUNT);

        dispatcher.dispatchPending();

        assertEquals(NotificationStatus.HELD, row(id).getStatus());
        assertNull(row(id).getChannel());
        assertTrue(email.sentIds.isEmpty());
        assertNull(row(id).getLastError(), "HELD is the whole story; no text is stored for it");

        dispatcher.dispatchPending();
        assertEquals(NotificationStatus.HELD, row(id).getStatus(), "still held, not lost and not defaulted");

        preferences.store(ACCOUNT, AlertChannel.EMAIL);
        dispatcher.dispatchPending();

        assertEquals(List.of(id), email.sentIds);
        assertSent(id, AlertChannel.EMAIL);
    }

    @Test
    void providerFailure_isRecordedAsFailedOnTheResolvedChannel() {
        preferences.store(ACCOUNT, AlertChannel.EMAIL);
        email.failWith("smtp unavailable");
        long id = queue(ACCOUNT);

        dispatcher.dispatchPending();

        Notification failed = row(id);
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        assertEquals(AlertChannel.EMAIL, failed.getChannel());
        assertEquals("smtp unavailable", failed.getLastError());
        assertNull(failed.getSentAt());

        dispatcher.dispatchPending();
        assertEquals(1, email.attempts, "a FAILED notification is not picked up again");
    }

    @Test
    void channelWithoutSender_isFailedNotSilentlyDropped() {
        preferences.store(ACCOUNT, AlertChannel.PUSH);
        long id = queue(ACCOUNT);

        dispatcher.dispatchPending();

        assertEquals(NotificationStatus.FAILED, row(id).getStatus());
        assertEquals(AlertChannel.PUSH, row(id).getChannel());
    }

    @Test
    void sentNotification_isNotSentAgainOnTheNextPass() {
        preferences.store(ACCOUNT, AlertChannel.EMAIL);
        queue(ACCOUNT);

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        assertEquals(1, email.sentIds.size());
    }

    private void assertSent(long id, AlertChannel channel) {
        Notification sent = row(id);
        assertEquals(NotificationStatus.SENT, sent.getStatus());
        assertEquals(channel, sent.getChannel());
        assertNotNull(sent.getSentAt());
        assertNull(sent.getLastError());
    }

    private long queue(long accountId) {
        Notification notification = new Notification();
        notification.setEventId(UUID.randomUUID());
        notification.setAccountId(accountId);
        notification.setType(NotificationType.ORDER_FILLED);
        notification.setMessage("Your BUY order for 10 AAPL was filled at 187.25.");
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setCreatedAt(OffsetDateTime.now());
        mapper.insertIfAbsent(notification);
        return notification.getNotificationId();
    }

    private Notification row(long id) {
        return mapper.rows().stream().filter(n -> n.getNotificationId() == id).findFirst().orElseThrow();
    }

    // stands in for the preferences module: read on every call, exactly like the real lookup
    private static final class StoredPreferences implements CustomerPreferencesLookup {
        private final Map<Long, AlertChannel> stored = new HashMap<>();

        void store(long accountId, AlertChannel channel) {
            stored.put(accountId, channel);
        }

        @Override
        public Optional<AlertChannel> findAlertChannel(long accountId) {
            return Optional.ofNullable(stored.get(accountId));
        }
    }

    private static final class RecordingSender implements ChannelSender {
        private final AlertChannel channel;
        private final List<Long> sentIds = new ArrayList<>();
        private String failure;
        private int attempts;

        RecordingSender(AlertChannel channel) {
            this.channel = channel;
        }

        void failWith(String message) {
            this.failure = message;
        }

        @Override
        public AlertChannel channel() {
            return channel;
        }

        @Override
        public void send(Notification notification) {
            attempts++;
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            sentIds.add(notification.getNotificationId());
        }
    }
}
