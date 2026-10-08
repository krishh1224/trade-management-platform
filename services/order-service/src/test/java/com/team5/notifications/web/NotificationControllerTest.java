package com.team5.notifications.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.notifications.service.MessageFormatter;
import com.team5.notifications.service.NotificationServiceImpl;
import com.team5.preferences.AlertChannel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

// The real service and authorizer sit behind the controller, so a 403 here is the actual
// ownership check refusing, not a mock told to throw.
@WebMvcTest(NotificationController.class)
@Import({
    SecurityConfig.class,
    AuthWebMvcConfig.class,
    NotificationServiceImpl.class,
    MessageFormatter.class,
    AccountAccessAuthorizer.class
})
class NotificationControllerTest {

    private static final long OWNER = 101L;
    private static final long OTHER_ACCOUNT = 202L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationMapper notificationMapper;

    @Test
    void listNotifications_owner_200() throws Exception {
        when(notificationMapper.findByAccount(OWNER, null, 50)).thenReturn(List.of(sentNotification()));

        mockMvc.perform(get("/api/v1/accounts/{id}/notifications", OWNER).with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].notificationId").value(42))
            .andExpect(jsonPath("$[0].type").value("ORDER_REJECTED"))
            .andExpect(jsonPath("$[0].title").value("Order rejected"))
            .andExpect(jsonPath("$[0].status").value("SENT"))
            .andExpect(jsonPath("$[0].channel").value("EMAIL"))
            .andExpect(jsonPath("$[0].message").value("Your BUY order for 5 TSLA was rejected (insufficient funds)."));
    }

    // Execution path 5 over HTTP: the token's accountId claim does not own the history
    @Test
    void listNotifications_anotherAccountsHistory_403AndNothingRead() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/notifications", OWNER).with(authenticatedAs(OTHER_ACCOUNT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(notificationMapper);
    }

    @Test
    void listNotifications_withoutToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/notifications", OWNER))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));

        verifyNoInteractions(notificationMapper);
    }

    @Test
    void listNotifications_statusFilterAndLimit_arePassedThrough() throws Exception {
        when(notificationMapper.findByAccount(OWNER, NotificationStatus.FAILED, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/{id}/notifications", OWNER)
                .param("status", "FAILED")
                .param("limit", "10")
                .with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        verify(notificationMapper).findByAccount(OWNER, NotificationStatus.FAILED, 10);
    }

    @Test
    void listNotifications_limitOutOfRange_422() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/notifications", OWNER)
                .param("limit", "0")
                .with(authenticatedAs(OWNER)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void listNotifications_unknownStatus_422() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/notifications", OWNER)
                .param("status", "DELIVERED")
                .with(authenticatedAs(OWNER)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void getNotification_owner_200() throws Exception {
        when(notificationMapper.findByIdForAccount(42L, OWNER)).thenReturn(sentNotification());

        mockMvc.perform(get("/api/v1/accounts/{id}/notifications/{nid}", OWNER, 42).with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").value(42))
            .andExpect(jsonPath("$.orderId").value(9002));
    }

    @Test
    void getNotification_belongingToAnotherAccount_404() throws Exception {
        // the row exists for OWNER; the other account asks for it under its own id
        when(notificationMapper.findByIdForAccount(42L, OTHER_ACCOUNT)).thenReturn(null);

        mockMvc.perform(get("/api/v1/accounts/{id}/notifications/{nid}", OTHER_ACCOUNT, 42)
                .with(authenticatedAs(OTHER_ACCOUNT)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NTF-404"));
    }

    @Test
    void getNotification_underAnotherAccountsPath_403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/notifications/{nid}", OWNER, 42)
                .with(authenticatedAs(OTHER_ACCOUNT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(notificationMapper);
    }

    @Test
    void markRead_owner_200() throws Exception {
        Notification read = sentNotification();
        read.setReadAt(OffsetDateTime.parse("2026-10-07T09:05:00Z"));
        when(notificationMapper.findByIdForAccount(42L, OWNER)).thenReturn(sentNotification(), read);

        mockMvc.perform(put("/api/v1/accounts/{id}/notifications/{nid}/read", OWNER, 42).with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readAt").value("2026-10-07T09:05:00Z"));

        verify(notificationMapper).markRead(eq(42L), eq(OWNER), any());
    }

    @Test
    void markRead_anotherAccount_403() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/notifications/{nid}/read", OWNER, 42)
                .with(authenticatedAs(OTHER_ACCOUNT)))
            .andExpect(status().isForbidden());

        verifyNoInteractions(notificationMapper);
    }

    // There is no route that creates or sends a notification: alert delivery is a Java interface
    @Test
    void noRouteCreatesANotification() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/notifications", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType("application/json")
                .content("{\"message\":\"anything at all\"}"))
            .andExpect(status().is4xxClientError());

        verify(notificationMapper, never()).insertIfAbsent(any());
        verify(notificationMapper, never()).findByAccount(anyLong(), isNull(), anyInt());
    }

    private static Notification sentNotification() {
        Notification notification = new Notification();
        notification.setNotificationId(42L);
        notification.setEventId(UUID.fromString("8a1f0c6e-5b5a-4c1e-9f0e-3c2b1a0d9e8f"));
        notification.setAccountId(OWNER);
        notification.setType(NotificationType.ORDER_REJECTED);
        notification.setOrderId(9002L);
        notification.setMessage("Your BUY order for 5 TSLA was rejected (insufficient funds).");
        notification.setStatus(NotificationStatus.SENT);
        notification.setChannel(AlertChannel.EMAIL);
        notification.setCreatedAt(OffsetDateTime.parse("2026-10-07T09:00:00Z"));
        notification.setSentAt(OffsetDateTime.parse("2026-10-07T09:00:05Z"));
        return notification;
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token
            .claim("accountId", accountId)
            .claim("iss", "auth-service")
        );
    }
}
