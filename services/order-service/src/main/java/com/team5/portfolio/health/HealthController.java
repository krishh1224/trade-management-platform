package com.team5.portfolio.health;

import com.team5.portfolio.config.FauxnanceProperties;
import com.team5.portfolio.pricing.FauxnanceClient;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness plus dependency status. Always answers 200 while the service runs: a failing dependency
 * degrades {@code status} but must not get the container restarted. Fauxnance's usage call costs
 * quota, so its result is cached for {@code fauxnance.usage-cache-ttl}.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final FauxnanceClient fauxnanceClient;
    private final FauxnanceProperties properties;
    private final Clock clock;

    private volatile int cachedQuota = -1;
    private volatile Instant quotaCheckedAt = Instant.MIN;

    public HealthController(
        DataSource dataSource, FauxnanceClient fauxnanceClient, FauxnanceProperties properties, Clock clock
    ) {
        this.dataSource = dataSource;
        this.fauxnanceClient = fauxnanceClient;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> postgres = dependency("postgres", postgresUp() ? "ok" : "down", null);
        int quota = fauxnanceQuota();
        String fauxnanceStatus = quota < 0 ? "down" : quota == 0 ? "degraded" : "ok";
        Map<String, Object> fauxnance = dependency("fauxnance", fauxnanceStatus, quota < 0 ? null : quota);

        boolean allOk = "ok".equals(postgres.get("status")) && "ok".equals(fauxnance.get("status"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allOk ? "ok" : "degraded");
        body.put("dependencies", List.of(postgres, fauxnance));
        body.put("asOf", clock.instant().truncatedTo(ChronoUnit.SECONDS));
        return ResponseEntity.ok(body);
    }

    private boolean postgresUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            log.warn("Postgres health check failed: {}", e.getMessage());
            return false;
        }
    }

    private int fauxnanceQuota() {
        if (!fauxnanceClient.isConfigured()) {
            return -1;
        }
        Instant now = clock.instant();
        if (quotaCheckedAt.plus(properties.usageCacheTtl()).isBefore(now)) {
            cachedQuota = fauxnanceClient.quotaRemaining();
            quotaCheckedAt = now;
        }
        return cachedQuota;
    }

    private static Map<String, Object> dependency(String name, String status, Integer quotaRemaining) {
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("name", name);
        dependency.put("status", status);
        if ("fauxnance".equals(name)) {
            dependency.put("quotaRemaining", quotaRemaining);
        }
        return dependency;
    }
}
