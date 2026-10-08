package com.team5.portfolio.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FauxnanceProperties.class)
public class PortfolioConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock portfolioClock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient fauxnanceRestClient(FauxnanceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        RestClient.Builder builder = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory);
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("x-api-key", properties.apiKey());
        }
        return builder.build();
    }
}
