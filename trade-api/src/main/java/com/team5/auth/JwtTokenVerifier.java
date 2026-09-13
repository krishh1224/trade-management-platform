package com.team5.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team5.exception.UnauthorisedException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenVerifier {

    private final ObjectMapper objectMapper;
    private final String sharedSecret;
    private final String expectedAlgorithm;

    public JwtTokenVerifier(
        ObjectMapper objectMapper,
        @Value("${auth.jwt.shared-secret:trade-api-shared-secret}") String sharedSecret,
        @Value("${auth.jwt.algorithm:HS256}") String expectedAlgorithm
    ) {
        this.objectMapper = objectMapper;
        this.sharedSecret = sharedSecret;
        this.expectedAlgorithm = expectedAlgorithm;
    }

    public Long verifyAndGetAccountId(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorisedException();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorisedException();
        }

        String encodedHeader = parts[0];
        String encodedPayload = parts[1];
        String encodedSignature = parts[2];
        String signingInput = encodedHeader + "." + encodedPayload;

        verifySignature(signingInput, encodedSignature);
        verifyExpiry(encodedPayload);
        verifyAlgorithm(encodedHeader);

        Map<String, Object> payload = decodeJsonPart(encodedPayload);
        Object accountIdValue = payload.get("accountId");
        if (accountIdValue instanceof Number accountIdNumber) {
            return accountIdNumber.longValue();
        }
        throw new UnauthorisedException();
    }

    private void verifySignature(String signingInput, String encodedSignature) {
        byte[] receivedSignature;
        try {
            receivedSignature = Base64.getUrlDecoder().decode(encodedSignature);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorisedException();
        }

        byte[] expectedSignature = hmac(signingInput.getBytes(StandardCharsets.UTF_8));
        if (!java.security.MessageDigest.isEqual(expectedSignature, receivedSignature)) {
            throw new UnauthorisedException();
        }
    }

    private void verifyExpiry(String encodedPayload) {
        Map<String, Object> payload = decodeJsonPart(encodedPayload);
        Object expValue = payload.get("exp");
        if (!(expValue instanceof Number expNumber)) {
            throw new UnauthorisedException();
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        if (expNumber.longValue() <= nowEpochSeconds) {
            throw new UnauthorisedException();
        }
    }

    private void verifyAlgorithm(String encodedHeader) {
        Map<String, Object> header = decodeJsonPart(encodedHeader);
        Object algValue = header.get("alg");
        if (!(algValue instanceof String algorithm)) {
            throw new UnauthorisedException();
        }

        if (!expectedAlgorithm.equals(algorithm)) {
            throw new UnauthorisedException();
        }
    }

    private byte[] hmac(byte[] content) {
        try {
            String jcaAlgorithm = mapMacAlgorithm(expectedAlgorithm);
            Mac mac = Mac.getInstance(jcaAlgorithm);
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), jcaAlgorithm));
            return mac.doFinal(content);
        } catch (Exception exception) {
            throw new UnauthorisedException();
        }
    }

    private Map<String, Object> decodeJsonPart(String encodedValue) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedValue);
            return objectMapper.readValue(decoded, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new UnauthorisedException();
        }
    }

    private String mapMacAlgorithm(String algorithm) {
        return switch (algorithm) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> throw new UnauthorisedException();
        };
    }
}