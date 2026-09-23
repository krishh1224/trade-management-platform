package com.neueda.learning.config;

import com.neueda.learning.exception.PermanentFailureException;
import com.neueda.learning.exception.TransientFailureException;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
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
     * - DeadLetterPublishingRecoverer to route failed messages to {topic}.DLT
     * - Immediate (no-retry) routing for permanent failures
     *
     * The DefaultErrorHandler will:
     * 1. For PermanentFailureException: immediately send to DLT via DeadLetterPublishingRecoverer, no retry
     * 2. For other exceptions: retry with exponential backoff, then send to DLT via DeadLetterPublishingRecoverer
     *
     * DLT routing happens via DeadLetterPublishingRecoverer which publishes the original message
     * to {topic}.DLT topic (e.g., "orders" → "orders.DLT") when:
     * - A PermanentFailureException is encountered (no retry)
     * - Retries are exhausted for transient failures
     *
     * @param kafkaTemplate KafkaTemplate for publishing to DLT
     * @return configured DefaultErrorHandler with DLT routing
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // Create exponential backoff configuration for transient failures
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(retryInitialIntervalMs);
        backOff.setMaxInterval(retryMaxIntervalMs);
        backOff.setMultiplier(retryMultiplier);
        backOff.setMaxElapsedTime(retryMaxIntervalMs * retryMaxAttempts);

        // Create DeadLetterPublishingRecoverer to route failed messages to {topic}.DLT
        // This recoverer publishes the original message to a topic named "{originalTopic}.DLT"
        // For example, a failed message from "orders" topic goes to "orders.DLT"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, exception) -> {
                // Determine the DLT topic name: append ".DLT" to original topic
                String dltTopicName = record.topic() + ".DLT";
                // All DLT messages go to partition 0 (DLTs have only 1 partition per design)
                return new TopicPartition(dltTopicName, 0);
            }
        );
        recoverer.setHeadersFunction((record, exception) -> {
            Headers headers = new RecordHeaders();
            headers.add("x-failure-reason", resolveFailureReason(exception).getBytes(StandardCharsets.UTF_8));
            return headers;
        });

        // Create default error handler with backoff and DLT recovery
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Configure exception-based routing:
        // PermanentFailureException: do not retry, send to DLT immediately
        // When this exception is thrown, the error handler will:
        // 1. NOT retry (because it's marked as non-retryable)
        // 2. Immediately invoke the recoverer to publish to DLT
        errorHandler.addNotRetryableExceptions(
            PermanentFailureException.class,
            DeserializationException.class,
            SerializationException.class,
            ConversionException.class,
            MessageConversionException.class
        );

        // For all other exceptions (including TransientFailureException),
        // the DefaultErrorHandler will:
        // 1. Retry with exponential backoff (100ms → 200ms → 400ms)
        // 2. If all retries are exhausted, invoke the recoverer to publish to DLT

        return errorHandler;
    }

    private String resolveFailureReason(Throwable exception) {
        if (exception == null) {
            return "UNKNOWN_FAILURE";
        }

        Throwable root = unwrap(exception);

        if (root instanceof PermanentFailureException pfe) {
            return pfe.getFailureReason();
        }
        if (root instanceof TransientFailureException tfe) {
            return tfe.getFailureReason();
        }
        if (root instanceof DeserializationException) {
            return "MALFORMED_JSON";
        }

        String className = root.getClass().getSimpleName();
        if ("RecordDeserializationException".equals(className)) {
            return "MALFORMED_JSON";
        }

        return className;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;

        if (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
            current = current.getCause();
        }

        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        return current;
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
    //
    // DLT Routing (automatic via DeadLetterPublishingRecoverer):
    // - Spring Boot auto-creates KafkaTemplate bean for DLT publishing
    // - PermanentFailureException → no retry → DLT immediately
    // - Other exceptions → retry with backoff → DLT after exhaustion
    // - Topic naming: {topic} → {topic}.DLT (e.g., orders → orders.DLT)
}



