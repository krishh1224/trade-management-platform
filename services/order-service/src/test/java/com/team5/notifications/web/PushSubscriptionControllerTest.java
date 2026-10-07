package com.team5.notifications.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.notifications.mapper.PushSubscriptionMapper;
import com.team5.notifications.service.PushSubscriptionService;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Real service and authorizer behind the controller; only the database mapper is mocked
@WebMvcTest(PushSubscriptionController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class, PushSubscriptionService.class, AccountAccessAuthorizer.class})
@TestPropertySource(properties = {
    "notifications.push.enabled=true",
    "notifications.push.vapid-public-key=BPublicKeyForTheBrowser"
})
class PushSubscriptionControllerTest {

    private static final long OWNER = 101L;
    private static final String CHROME = "https://fcm.googleapis.com/fcm/send/abc";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PushSubscriptionMapper subscriptionMapper;

    @Test
    void config_givesTheOwnerThePublicKey() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/push-subscription/config", OWNER).with(authenticatedAs(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.publicKey").value("BPublicKeyForTheBrowser"));
    }

    @Test
    void subscribe_owner_204() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/push-subscription", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CHROME, validP256dh(), validAuth())))
            .andExpect(status().isNoContent());

        verify(subscriptionMapper).upsert(OWNER, CHROME, validP256dh(), validAuth());
    }

    // the SSRF rule: the platform must never be talked into POSTing to a URL a customer chose
    @Test
    void subscribe_endpointOutsideThePushServices_422AndNothingStored() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/push-subscription", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("https://169.254.169.254/latest/meta-data/", validP256dh(), validAuth())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));

        verify(subscriptionMapper, never()).upsert(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void subscribe_malformedKeys_422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/push-subscription", OWNER)
                .with(authenticatedAs(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CHROME, "not-a-key", validAuth())))
            .andExpect(status().isUnprocessableEntity());

        verify(subscriptionMapper, never()).upsert(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void subscribe_forAnotherAccount_403() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/push-subscription", OWNER)
                .with(authenticatedAs(202L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CHROME, validP256dh(), validAuth())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verifyNoInteractions(subscriptionMapper);
    }

    @Test
    void subscribe_withoutToken_401() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/{id}/push-subscription", OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CHROME, validP256dh(), validAuth())))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(subscriptionMapper);
    }

    @Test
    void unsubscribe_removesOnlyThisAccountsBrowser() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}/push-subscription", OWNER)
                .param("endpoint", CHROME)
                .with(authenticatedAs(OWNER)))
            .andExpect(status().isNoContent());

        verify(subscriptionMapper).deleteForAccount(OWNER, CHROME);
    }

    private static String body(String endpoint, String p256dh, String auth) {
        return """
            {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}
            """.formatted(endpoint, p256dh, auth);
    }

    private static String validP256dh() {
        byte[] point = new byte[65];
        point[0] = 4;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(point);
    }

    private static String validAuth() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token.claim("accountId", accountId).claim("iss", "auth-service"));
    }
}
