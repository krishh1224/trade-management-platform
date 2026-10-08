package com.team5.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The delegated token an automated strategy places its orders with (see the strategies module README,
 * "Identity"). It is signed with its own key, never the customer key, carries the strategy owner's
 * account id, and is accepted on {@code POST /api/v1/orders} only.
 *
 * With no secret configured the Trade API accepts no strategy tokens at all and the strategies module
 * cannot place orders.
 */
@ConfigurationProperties(prefix = "auth.strategy-token")
public record StrategyTokenProperties(String sharedSecret, Duration ttl) {

    public static final String ISSUER = "strategy-automation";
    public static final String SCOPE_CLAIM = "scope";
    public static final String ORDERS_PLACE_SCOPE = "orders:place";
    public static final String SUBJECT_PREFIX = "strategy:";

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    private static final Duration MAX_TTL = Duration.ofMinutes(5);

    public StrategyTokenProperties {
        if (ttl == null) {
            ttl = DEFAULT_TTL;
        }
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalStateException("auth.strategy-token.ttl must be between 1s and 5m but was " + ttl);
        }
    }

    public boolean configured() {
        return sharedSecret != null && !sharedSecret.isBlank();
    }
}
