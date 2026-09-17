package com.neueda.learning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Kafka consumer configuration.
 * Most settings are configured via application.properties.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    // Kafka consumer configuration via Spring Boot application.properties:
    // spring.kafka.bootstrap-servers
    // spring.kafka.consumer.group-id=trade-executor
    // spring.kafka.consumer.value-deserializer=...JsonDeserializer
    // spring.kafka.consumer.properties.spring.json.trusted.packages=...
}


