package com.team5.strategies.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the strategies module (see its README, "Configuration").
 *
 * @param maxPerAccount   strategies one account may hold, in any status
 * @param priceTolerance  how far past the quoted ask (BUY) or bid (SELL) the limit price is set, as a
 *                        fraction: 0.01 means a BUY limit 1% above the ask. The executor re-quotes when it
 *                        fills and rejects a BUY whose limit is below the ask then.
 * @param decisionDelay   pause between a rule firing and the order decision. Zero in production; a few
 *                        seconds in a demonstration, to disable a strategy between its trigger and its order.
 * @param tradeApiBaseUrl where POST /api/v1/orders is reached; this same service, over HTTP
 * @param connectTimeout  connect timeout for that call
 * @param readTimeout     read timeout for that call; a strategy row stays locked for at most this long
 * @param maxQuoteAge     a quote older than this when it is consumed (a backlog after downtime) still
 *                        extends the price history but is never traded on
 */
@ConfigurationProperties(prefix = "strategies")
public record StrategyProperties(
    Integer maxPerAccount,
    BigDecimal priceTolerance,
    Duration decisionDelay,
    String tradeApiBaseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration maxQuoteAge
) {

    public StrategyProperties {
        if (maxQuoteAge == null) {
            maxQuoteAge = Duration.ofMinutes(5);
        }
        if (maxQuoteAge.isNegative() || maxQuoteAge.isZero() || maxQuoteAge.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("strategies.max-quote-age must be between 1s and 1h");
        }
        if (maxPerAccount == null) {
            maxPerAccount = 10;
        }
        if (maxPerAccount < 1) {
            throw new IllegalStateException("strategies.max-per-account must be at least 1");
        }
        if (priceTolerance == null) {
            priceTolerance = new BigDecimal("0.01");
        }
        if (priceTolerance.signum() < 0 || priceTolerance.compareTo(new BigDecimal("0.10")) > 0) {
            throw new IllegalStateException("strategies.price-tolerance must be between 0 and 0.10");
        }
        if (decisionDelay == null) {
            decisionDelay = Duration.ZERO;
        }
        if (decisionDelay.isNegative() || decisionDelay.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalStateException("strategies.decision-delay must be between 0s and 1m");
        }
        if (tradeApiBaseUrl == null || tradeApiBaseUrl.isBlank()) {
            tradeApiBaseUrl = "http://localhost:8081";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
