package com.team5.strategies.consumer;

import com.team5.strategies.exception.UnusableQuoteException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// Consumer for the strategies module. Its own group id means its offsets on market-data are independent
// of the watchlists consumer and every other one, and it reads market-data only.
@Configuration
@EnableKafka
public class StrategyConsumerConfig {

    static final String CONTAINER_FACTORY = "strategyMarketDataContainerFactory";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${strategies.consumer.group-id:strategies-market-data}")
    private String groupId;

    @Value("${strategies.consumer.retry-interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${strategies.consumer.max-retries:3}")
    private long maxRetries;

    @Bean(name = CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> strategyMarketDataContainerFactory(
        KafkaTemplate<String, String> kafkaTemplate
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // raw string: the listener parses it, so malformed JSON becomes our own non-retryable error
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // offsets are committed by the listener, after the quote's cycle has finished
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // a new group starts at the end: a strategy must never trade on a backlog of old prices
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    // Database down mid-cycle: retry a few times, then park the quote on market-data.DLT rather than
    // block the partition. A retried quote is REDELIVERED, so strategies it already acted on are skipped.
    // A quote that can never be evaluated goes to the DLT straight away.
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, exception) -> new TopicPartition(record.topic() + ".DLT", 0));
        recoverer.setHeadersFunction((record, exception) -> {
            RecordHeaders headers = new RecordHeaders();
            headers.add("x-failure-reason", failureReason(exception).getBytes(StandardCharsets.UTF_8));
            headers.add("x-failed-consumer-group", groupId.getBytes(StandardCharsets.UTF_8));
            return headers;
        });

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, maxRetries));
        handler.addNotRetryableExceptions(UnusableQuoteException.class);
        return handler;
    }

    private String failureReason(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnusableQuoteException unusable) {
                return unusable.getFailureReason();
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
