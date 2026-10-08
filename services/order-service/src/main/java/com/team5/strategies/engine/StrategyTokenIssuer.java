package com.team5.strategies.engine;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.team5.auth.AccountClaims;
import com.team5.auth.StrategyTokenProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Mints the token one strategy order is placed with. A new token per order, alive for the configured
 * ttl (60 s by default), naming the strategy as subject and the strategy's owner as the account, with
 * the single scope orders:place. The Trade API accepts it on POST /api/v1/orders and nowhere else.
 *
 * The account id is the one stored on the strategy, which was taken from the customer's own token
 * when they created it. Nothing a quote carries can change it.
 */
@Component
public class StrategyTokenIssuer {

    private final StrategyTokenProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;

    public StrategyTokenIssuer(StrategyTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.encoder = properties.configured()
            ? new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                properties.sharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")))
            : null;
    }

    public boolean configured() {
        return encoder != null;
    }

    public String issue(long strategyId, long accountId) {
        if (encoder == null) {
            throw new IllegalStateException("auth.strategy-token.shared-secret (STRATEGY_TOKEN_SECRET) is not set");
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(StrategyTokenProperties.ISSUER)
            .subject(StrategyTokenProperties.SUBJECT_PREFIX + strategyId)
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.ttl()))
            .claim(AccountClaims.ACCOUNT_ID, accountId)
            .claim(StrategyTokenProperties.SCOPE_CLAIM, StrategyTokenProperties.ORDERS_PLACE_SCOPE)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
