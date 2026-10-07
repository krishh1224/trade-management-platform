package com.team5.watchlists.consumer;

import com.team5.watchlists.exception.InvalidQuoteException;
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

// Consumer for the watchlists module, separate from anything else in the Trade REST API. Its own
// group id means its offsets on market-data are independent of every other consumer, and it reads
// market-data only: never orders, never trade-events.
@Configuration
@EnableKafka
public class WatchlistConsumerConfig {

    static final String CONTAINER_FACTORY = "watchlistMarketDataContainerFactory";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${watchlists.consumer.group-id:watchlists-market-data}")
    private String groupId;

    @Value("${watchlists.consumer.retry-interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${watchlists.consumer.max-retries:3}")
    private long maxRetries;

    @Bean(name = CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> watchlistMarketDataContainerFactory(
        KafkaTemplate<String, String> kafkaTemplate
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // raw string: the listener parses it, so malformed JSON becomes our own non-retryable error
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // offsets are committed by the listener, after the quote's transaction commits
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    // Database down or notifications refusing the hand-off: retry a few times, then park the quote on
    // market-data.DLT rather than block the partition. The next quote for the symbol is measured from
    // the last price that did commit, so a crossing that failed to be handed over is found again.
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
        handler.addNotRetryableExceptions(InvalidQuoteException.class);
        return handler;
    }

    private String failureReason(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InvalidQuoteException invalid) {
                return invalid.getFailureReason();
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
