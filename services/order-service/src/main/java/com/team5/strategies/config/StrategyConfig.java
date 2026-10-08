package com.team5.strategies.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StrategyProperties.class)
public class StrategyConfig {
}
