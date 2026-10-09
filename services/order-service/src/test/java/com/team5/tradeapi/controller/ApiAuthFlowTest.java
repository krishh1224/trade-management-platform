package com.team5.tradeapi.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.controller.AccountController;
import com.team5.domain.enums.AccountStatus;
import com.team5.dto.response.AccountResponse;
import com.team5.mapper.AccountQueryMapper;
import com.team5.mapper.TransactionMapper;
import com.team5.notifications.service.NotificationService;
import com.team5.service.AccountServiceImpl;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class, AccountServiceImpl.class, AccountAccessAuthorizer.class})
@TestPropertySource(properties = {
    "auth.jwt.shared-secret=" + ApiAuthFlowTest.SECRET,
    "auth.jwt.algorithm=HS256",
    "auth.jwt.clock-skew=0s"
})
class ApiAuthFlowTest {

    static final String SECRET = "test-only-shared-secret-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountQueryMapper accountQueryMapper;

    @MockBean
    private TransactionMapper transactionMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    void missingAuthorisationHeader_shouldReturn401WithUniformBody() throws Exception {
        expectUnauthorised(mockMvc.perform(get("/api/v1/accounts/{id}", 1L)));
    }

    @Test
    void wrongScheme_shouldReturn401WithUniformBody() throws Exception {
        expectUnauthorised(mockMvc.perform(get("/api/v1/accounts/{id}", 1L)
            .header("Authorization", "Basic abc")));
    }

    @Test
    void malformedToken_shouldReturn401WithUniformBody() throws Exception {
        expectUnauthorised(withBearer("not-a-jwt"));
    }

    @Test
    void expiredToken_shouldReturn401WithUniformBody() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS256", SECRET, "{\"accountId\":1,\"exp\":" + epochOffset(-10) + "}")));
    }

    @Test
    void forgedSignature_shouldReturn401WithUniformBody() throws Exception {
        String wrongSecret = "some-other-secret-that-is-also-32-bytes-long";
        expectUnauthorised(withBearer(signedToken("HS256", wrongSecret, validPayload(1))));
    }

    @Test
    void wrongAlgorithm_shouldReturn401WithUniformBody() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS512", SECRET + SECRET, validPayload(1))));
    }

    @Test
    void headerAlgorithmNotMatchingSignature_shouldReturn401() throws Exception {
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"HS512\"}");
        String payload = encode(validPayload(1));
        String signature = sign("HmacSHA256", SECRET, header + "." + payload);
        expectUnauthorised(withBearer(header + "." + payload + "." + signature));
    }

    @Test
    void unsignedAlgNoneToken_shouldReturn401() throws Exception {
        String token = encode("{\"typ\":\"JWT\",\"alg\":\"none\"}") + "." + encode(validPayload(1)) + ".";
        expectUnauthorised(withBearer(token));
    }

    @Test
    void tokenWithoutExp_shouldReturn401() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS256", SECRET, "{\"accountId\":1}")));
    }

    @Test
    void tokenWithoutAccountId_shouldReturn401() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS256", SECRET, "{\"exp\":" + epochOffset(600) + "}")));
    }

    @Test
    void fractionalAccountId_shouldReturn401InsteadOfTruncating() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS256", SECRET, "{\"accountId\":1.9,\"exp\":" + epochOffset(600) + "}")));
    }

    @Test
    void stringAccountId_shouldReturn401() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS256", SECRET, "{\"accountId\":\"1\",\"exp\":" + epochOffset(600) + "}")));
    }

    @Test
    void nonPositiveAccountId_shouldReturn401() throws Exception {
        expectUnauthorised(withBearer(signedToken("HS256", SECRET, "{\"accountId\":0,\"exp\":" + epochOffset(600) + "}")));
    }

    @Test
    void tokenInQueryParameter_shouldNotAuthenticate() throws Exception {
        String token = signedToken("HS256", SECRET, validPayload(1));
        expectUnauthorised(mockMvc.perform(get("/api/v1/accounts/{id}", 1L).queryParam("access_token", token)));
    }

    @Test
    void validTokenForDifferentAccount_shouldReturn403WithAccountNotActiveMessage() throws Exception {
        withBearer(signedToken("HS256", SECRET, validPayload(2)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"))
            .andExpect(jsonPath("$.message").value("Account not active"));

        verifyNoInteractions(accountQueryMapper);
    }

    @Test
    void validTokenForOwnAccount_shouldReturn200() throws Exception {
        when(accountQueryMapper.findAccountForOrder(1L)).thenReturn(Map.of("status", "ACTIVE"));
        when(accountQueryMapper.getAccount(anyLong())).thenReturn(new AccountResponse(
            1L, "ACC-000001", "Priya Menon", BigDecimal.TEN, AccountStatus.ACTIVE, 1,
            OffsetDateTime.parse("2026-09-09T14:00:00Z")));

        withBearer(signedToken("HS256", SECRET, validPayload(1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    private ResultActions withBearer(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/{id}", 1L).header("Authorization", "Bearer " + token));
    }

    private void expectUnauthorised(ResultActions result) throws Exception {
        result
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("Unauthorised"));
        verifyNoInteractions(accountQueryMapper);
    }

    private static String validPayload(long accountId) {
        return "{\"accountId\":" + accountId + ",\"iss\":\"auth-service\",\"exp\":" + epochOffset(600) + "}";
    }

    private static long epochOffset(long seconds) {
        return Instant.now().plusSeconds(seconds).getEpochSecond();
    }

    private static String signedToken(String alg, String secret, String payloadJson) throws Exception {
        String macAlgorithm = switch (alg) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };
        String signingInput = encode("{\"typ\":\"JWT\",\"alg\":\"" + alg + "\"}") + "." + encode(payloadJson);
        return signingInput + "." + sign(macAlgorithm, secret, signingInput);
    }

    private static String sign(String macAlgorithm, String secret, String signingInput) throws Exception {
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
