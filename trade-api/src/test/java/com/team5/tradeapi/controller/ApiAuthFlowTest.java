package com.team5.tradeapi.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.JwtAuthFilter;
import com.team5.auth.JwtTokenVerifier;
import com.team5.controller.AccountController;
import com.team5.service.AccountService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import({JwtAuthFilter.class, JwtTokenVerifier.class, AccountAccessAuthorizer.class})
@TestPropertySource(properties = {
    "auth.jwt.shared-secret=test-shared-secret",
    "auth.jwt.algorithm=HS256"
})
class ApiAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Test
    void missingAuthorisationHeader_shouldReturn401WithUniformBody() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", 1L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("Unauthorised"));

        verifyNoInteractions(accountService);
    }

    @Test
    void wrongScheme_shouldReturn401WithUniformBody() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", 1L)
                .header("Authorization", "Basic abc"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("Unauthorised"));

        verifyNoInteractions(accountService);
    }

    @Test
    void expiredToken_shouldReturn401WithUniformBody() throws Exception {
        String token = buildToken(1L, Instant.now().minusSeconds(10).getEpochSecond(), "HS256", "test-shared-secret");

        mockMvc.perform(get("/api/v1/accounts/{id}", 1L)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("Unauthorised"));

        verifyNoInteractions(accountService);
    }

    @Test
    void forgedSignature_shouldReturn401WithUniformBody() throws Exception {
        String token = buildToken(1L, Instant.now().plusSeconds(600).getEpochSecond(), "HS256", "wrong-secret");

        mockMvc.perform(get("/api/v1/accounts/{id}", 1L)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("Unauthorised"));

        verifyNoInteractions(accountService);
    }

    @Test
    void wrongAlgorithm_shouldReturn401WithUniformBody() throws Exception {
        String token = buildToken(1L, Instant.now().plusSeconds(600).getEpochSecond(), "HS512", "test-shared-secret");

        mockMvc.perform(get("/api/v1/accounts/{id}", 1L)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("Unauthorised"));

        verifyNoInteractions(accountService);
    }

    @Test
    void validTokenForDifferentAccount_shouldReturn403WithAccountNotActiveMessage() throws Exception {
        String token = buildToken(2L, Instant.now().plusSeconds(600).getEpochSecond(), "HS256", "test-shared-secret");

        mockMvc.perform(get("/api/v1/accounts/{id}", 1L)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"))
            .andExpect(jsonPath("$.message").value("Account not active"));

        verifyNoInteractions(accountService);
    }

    private String buildToken(Long accountId, long exp, String alg, String signingSecret) throws Exception {
        String headerJson = "{\"typ\":\"JWT\",\"alg\":\"" + alg + "\"}";
        String payloadJson = "{\"accountId\":" + accountId + ",\"exp\":" + exp + "}";

        String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;

        String macAlgorithm = switch (alg) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };

        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
        String signature = base64UrlEncode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));

        return signingInput + "." + signature;
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
