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

@Configuration
public class RestTemplateConfig {

    @Value("${fauxnance.api-key:}")
    private String apiKey;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
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