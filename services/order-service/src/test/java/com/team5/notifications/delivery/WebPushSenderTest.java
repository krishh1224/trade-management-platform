package com.team5.notifications.delivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.domain.PushSubscription;
import com.team5.notifications.mapper.PushSubscriptionMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebPushSenderTest {

    private static final long ACCOUNT = 101L;
    private static final String CHROME = "https://fcm.googleapis.com/fcm/send/chrome";
    private static final String EDGE = "https://wns2-par02p.notify.windows.com/w/?token=edge";

    private PushSubscriptionMapper subscriptions;
    private PushGateway gateway;
    private WebPushSender sender;

    @BeforeEach
    void setUp() {
        subscriptions = mock(PushSubscriptionMapper.class);
        gateway = mock(PushGateway.class);
        sender = new WebPushSender(subscriptions, gateway);
    }

    @Test
    void sendsThePopUpTextToEveryBrowserOfTheAccount() throws Exception {
        when(subscriptions.findByAccount(ACCOUNT)).thenReturn(List.of(browser(1L, CHROME), browser(2L, EDGE)));
        when(gateway.send(anyString(), anyString(), anyString(), any())).thenReturn(201);

        sender.send(notification(NotificationType.ORDER_REJECTED, "Your BUY order for 1 TSLA was rejected (insufficient funds)."));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway).send(eq(CHROME), eq("p256dh-1"), eq("auth-1"), payload.capture());
        verify(gateway).send(eq(EDGE), eq("p256dh-2"), eq("auth-2"), any());
        JsonNode popUp = new ObjectMapper().readTree(new String(payload.getValue(), StandardCharsets.UTF_8));
        assertEquals("Artha: Order rejected", popUp.get("title").asText());
        assertEquals("Your BUY order for 1 TSLA was rejected (insufficient funds).", popUp.get("body").asText());
        assertEquals("/notifications", popUp.get("url").asText());
        assertEquals("artha-notification-7", popUp.get("tag").asText());
    }

    @Test
    void noBrowserEnabled_isAFailureNotASilentSuccess() {
        when(subscriptions.findByAccount(ACCOUNT)).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        assertEquals("Push is not enabled on any browser for this account", error.getMessage());
    }

    @Test
    void expiredSubscription_isForgotten() throws Exception {
        when(subscriptions.findByAccount(ACCOUNT)).thenReturn(List.of(browser(1L, CHROME)));
        when(gateway.send(anyString(), anyString(), anyString(), any())).thenReturn(410);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        verify(subscriptions).deleteById(1L);
        assertTrue(error.getMessage().contains("subscription expired (HTTP 410)"));
    }

    @Test
    void oneBrowserDelivered_isASuccessEvenIfAnotherExpired() throws Exception {
        when(subscriptions.findByAccount(ACCOUNT)).thenReturn(List.of(browser(1L, CHROME), browser(2L, EDGE)));
        when(gateway.send(eq(CHROME), anyString(), anyString(), any())).thenReturn(404);
        when(gateway.send(eq(EDGE), anyString(), anyString(), any())).thenReturn(201);

        assertDoesNotThrow(() -> sender.send(notification(NotificationType.ORDER_CANCELLED, "Your order was cancelled.")));

        verify(subscriptions).deleteById(1L);
        verify(subscriptions, never()).deleteById(2L);
    }

    @Test
    void storedEndpointOutsideTheAllowlist_isNeverContacted() throws Exception {
        when(subscriptions.findByAccount(ACCOUNT)).thenReturn(List.of(browser(9L, "https://10.8.72.147:8085/orders")));

        assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        verify(gateway, never()).send(anyString(), anyString(), anyString(), any());
        verify(subscriptions).deleteById(9L);
    }

    @Test
    void pushServiceError_becomesAShortReason() throws Exception {
        when(subscriptions.findByAccount(ACCOUNT)).thenReturn(List.of(browser(1L, CHROME)));
        when(gateway.send(anyString(), anyString(), anyString(), any())).thenThrow(new java.io.IOException("connection reset"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        assertEquals("Push not delivered: IOException", error.getMessage());
    }

    private static PushSubscription browser(long id, String endpoint) {
        return new PushSubscription(id, ACCOUNT, endpoint, "p256dh-" + id, "auth-" + id);
    }

    private static Notification notification(NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setNotificationId(7L);
        notification.setAccountId(ACCOUNT);
        notification.setType(type);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.QUEUED);
        return notification;
    }
}
