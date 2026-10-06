package com.team5.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(String sharedSecret, String algorithm, Duration clockSkew, String issuer) {

    public JwtProperties {
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = "HS256";
        }
        if (clockSkew == null) {
            clockSkew = Duration.ZERO;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "auth-service";
        }
    }
}
