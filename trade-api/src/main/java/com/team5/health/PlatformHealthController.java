package com.team5.health;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformHealthController {

    private final String datasourceUrl;
    private final String authStubBaseUrl;

    public PlatformHealthController(
        @Value("${spring.datasource.url}") String datasourceUrl,
        @Value("${auth.stub.base-url}") String authStubBaseUrl
    ) {
        this.datasourceUrl = datasourceUrl;
        this.authStubBaseUrl = authStubBaseUrl;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String postgresHost = extractPostgresHost(datasourceUrl);
        String authStubHost = extractHttpHost(authStubBaseUrl);

        boolean postgresResolvable = isResolvable(postgresHost);
        boolean authStubResolvable = isResolvable(authStubHost);
        boolean healthy = postgresResolvable && authStubResolvable;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", healthy ? "UP" : "DOWN");

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("postgresHost", postgresHost);
        checks.put("postgresResolvable", postgresResolvable);
        checks.put("authStubHost", authStubHost);
        checks.put("authStubResolvable", authStubResolvable);
        response.put("checks", checks);

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private String extractPostgresHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "unknown";
        }

        String url = jdbcUrl;
        int schemeIndex = url.indexOf("//");
        if (schemeIndex < 0) {
            return "unknown";
        }

        String hostAndRest = url.substring(schemeIndex + 2);
        int slashIndex = hostAndRest.indexOf('/');
        String hostPort = slashIndex >= 0 ? hostAndRest.substring(0, slashIndex) : hostAndRest;
        int colonIndex = hostPort.indexOf(':');
        return colonIndex >= 0 ? hostPort.substring(0, colonIndex) : hostPort;
    }

    private String extractHttpHost(String baseUrl) {
        try {
            URI uri = new URI(baseUrl);
            String host = uri.getHost();
            return host == null || host.isBlank() ? "unknown" : host;
        } catch (URISyntaxException exception) {
            return "unknown";
        }
    }

    private boolean isResolvable(String host) {
        if (host == null || host.isBlank() || "unknown".equals(host)) {
            return false;
        }

        try {
            InetAddress.getByName(host);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
