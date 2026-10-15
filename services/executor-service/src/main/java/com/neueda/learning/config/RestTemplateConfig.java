package com.neueda.learning.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${fauxnance.api-key}")
    private String apiKey;

    // Without these the client waits forever on a hung Fauxnance call, and because the order
    // consumer handles one message at a time, every order placed after it queues behind it.
    @Value("${fauxnance.connect-timeout:3s}")
    private Duration connectTimeout = Duration.ofSeconds(3);

    @Value("${fauxnance.read-timeout:8s}")
    private Duration readTimeout = Duration.ofSeconds(8);

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .interceptors(new ClientHttpRequestInterceptor() {
                @Override
                public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                        ClientHttpRequestExecution execution) throws IOException {
                    if (apiKey != null && !apiKey.isEmpty()) {
                        request.getHeaders().set("x-api-key", apiKey);
                    }
                    return execution.execute(request, body);
                }
            })
            .build();
    }
}