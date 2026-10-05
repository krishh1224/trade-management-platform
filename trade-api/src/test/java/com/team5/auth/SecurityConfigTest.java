package com.team5.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void missingSecretFailsStartup() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> securityConfig.jwtDecoder(new JwtProperties(null, "HS256", null, "auth-service")));
        assertTrue(exception.getMessage().contains("must be set"));
    }

    @Test
    void blankSecretFailsStartup() {
        assertThrows(IllegalStateException.class,
            () -> securityConfig.jwtDecoder(new JwtProperties("   ", "HS256", null, "auth-service")));
    }

    @Test
    void legacyShortSecretFailsStartup() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> securityConfig.jwtDecoder(new JwtProperties("trade-api-shared-secret", "HS256", null, "auth-service")));
        assertTrue(exception.getMessage().contains("at least 32 bytes"));
    }

    @Test
    void secretShorterThanHashOutputFailsForStrongerAlgorithms() {
        String thirtyTwoBytes = "0123456789abcdef0123456789abcdef";
        assertThrows(IllegalStateException.class,
            () -> securityConfig.jwtDecoder(new JwtProperties(thirtyTwoBytes, "HS512", null, "auth-service")));
    }

    @Test
    void unsupportedAlgorithmFailsStartup() {
        assertThrows(IllegalStateException.class,
            () -> securityConfig.jwtDecoder(new JwtProperties("0123456789abcdef0123456789abcdef", "RS256", null, "auth-service")));
    }

    @Test
    void strongSecretBuildsDecoder() {
        assertNotNull(securityConfig.jwtDecoder(new JwtProperties("0123456789abcdef0123456789abcdef", "HS256", null, "auth-service")));
    }
}
