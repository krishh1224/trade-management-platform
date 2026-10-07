package com.team5.advisor.chat;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Gemini client, with a timeout on the HTTP call.
 *
 * <p>Spring AI builds this client with no timeout, so when Gemini accepts a request and never answers (it does
 * under heavy load) the chat waits forever. Spring AI's own client backs off when one is defined here, so the
 * model, tools and Spring retry settings are unchanged. The SDK's own retries are turned off: it retries up to
 * five times by default, each attempt waiting out the full timeout, and Spring AI already retries
 * ({@code spring.ai.retry.max-attempts}).
 */
@Configuration
public class GeminiClientConfig {

    @Bean
    Client googleGenAiClient(
        @Value("${spring.ai.google.genai.api-key:}") String apiKey,
        @Value("${advisor.gemini.request-timeout:40s}") Duration requestTimeout
    ) {
        return build(apiKey, requestTimeout, null);
    }

    /** @param baseUrl overrides the Gemini endpoint; null for the real one (a test points it at a local server) */
    static Client build(String apiKey, Duration requestTimeout, String baseUrl) {
        HttpOptions.Builder http = HttpOptions.builder()
            .timeout(Math.toIntExact(requestTimeout.toMillis()))
            .retryOptions(HttpRetryOptions.builder().attempts(1));
        if (baseUrl != null) {
            http.baseUrl(baseUrl);
        }
        return Client.builder().apiKey(apiKey).httpOptions(http.build()).build();
    }
}
