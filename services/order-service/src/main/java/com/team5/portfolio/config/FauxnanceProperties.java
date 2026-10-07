package com.team5.portfolio.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fauxnance")
public record FauxnanceProperties(
    String baseUrl,
    String apiKey,
    @DefaultValue("60s") Duration cacheTtl,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("4s") Duration readTimeout,
    @DefaultValue("5m") Duration usageCacheTtl,
    @DefaultValue("1h") Duration candleCacheTtl
) {
}
