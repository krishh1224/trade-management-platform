package com.team5.advisor.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Same settings and defaults as trade-api, so a token trade-api accepts is accepted here. */
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
