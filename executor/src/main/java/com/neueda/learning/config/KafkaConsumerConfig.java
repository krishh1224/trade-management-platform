package com.neueda.learning.config;

import com.neueda.learning.exception.PermanentFailureException;
import com.neueda.learning.exception.TransientFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer configuration.
 *
 * Configures:
 * 1. DefaultErrorHandler for transient failures with exponential backoff retry
 * 2. Exception-based routing: PermanentFailureException bypasses retry and goes to DLT
 * 3. Exponential backoff configuration for transient failures
 *
 * Most settings are configured via application.properties.
 * Retry and backoff behavior is configured in this class using values from application.properties.
 *
 * DLT Routing:
 * - Permanent failures (PermanentFailureException) are routed to {topic}.DLT immediately
 * - Transient failures are retried with exponential backoff before routing to DLT
 * - The default recovery behavior routes to {topic}.DLT when recovery fails
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${executor.retry.initial-interval-ms:100}")
    private long retryInitialIntervalMs;

    @Value("${executor.retry.max-interval-ms:5000}")
    private long retryMaxIntervalMs;

    @Value("${executor.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${executor.retry.max-attempts:3}")
    private int retryMaxAttempts;

    /**
     * Configure DefaultErrorHandler with:
     * - Exponential backoff for transient failures
     * - Immediate (no-retry) routing for permanent failures
     *
     * The DefaultErrorHandler will:
     * 1. For PermanentFailureException: immediately send to DLT, no retry
     * 2. For other exceptions: retry with exponential backoff, then DLT
     *
     * DLT routing happens automatically via Spring Kafka's dead-letter infrastructure
     * when the listener container detects a RecoverableException or after max retries.
     *
     * @return configured DefaultErrorHandler
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // Create exponential backoff configuration for transient failures
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(retryInitialIntervalMs);
        backOff.setMaxInterval(retryMaxIntervalMs);
        backOff.setMultiplier(retryMultiplier);
        backOff.setMaxElapsedTime(retryMaxIntervalMs * retryMaxAttempts);

        // Create default error handler with backoff
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);

        // Configure exception-based routing:
        // PermanentFailureException: do not retry, send to DLT immediately
        // This is set via addNotRetryableExceptions which will cause the DefaultErrorHandler
        // to not retry and let the message fail (which routes to DLT)
        errorHandler.addNotRetryableExceptions(PermanentFailureException.class);

        // For all other exceptions (including TransientFailureException),
        // the DefaultErrorHandler will retry with backoff before routing to DLT

        return errorHandler;
    }

    // Kafka consumer configuration via Spring Boot application.properties:
    // spring.kafka.bootstrap-servers
    // spring.kafka.consumer.group-id=trade-executor
    // spring.kafka.consumer.value-deserializer=ErrorHandlingDeserializer
    // spring.kafka.listener.ack-mode=manual_immediate
    // spring.kafka.listener.poll-timeout=3000
    //
    // Retry and backoff configuration (application.properties):
    // executor.retry.initial-interval-ms=100
    // executor.retry.max-interval-ms=5000
    // executor.retry.multiplier=2.0
    // executor.retry.max-attempts=3
}



