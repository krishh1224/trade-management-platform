package com.neueda.learning.producer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.learning.domain.Quote;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class MarketDataPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MarketDataPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MarketDataPublisher(kafkaTemplate);
    }

    @Test
    void publishQuote_sendsEnvelopeToMarketDataTopicUsingSymbolKey() {
        when(kafkaTemplate.send(eq("market-data"), eq("INFY.NS"), anyString()))
            .thenReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publishQuote("INFY.NS", new Quote("INFY.NS", new BigDecimal("999.98"), new BigDecimal("1000.42")));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("market-data"), eq("INFY.NS"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("\"eventType\":\"QUOTE\""));
        assertTrue(payload.contains("\"symbol\":\"INFY.NS\""));
        assertTrue(payload.contains("\"bid\":999.98"));
        assertTrue(payload.contains("\"ask\":1000.42"));
    }

    @Test
    void publishQuoteSwallowsAsyncPublishFailure() {
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Topic market-data not present in metadata"));
        when(kafkaTemplate.send(eq("market-data"), eq("INFY.NS"), anyString())).thenReturn(failedFuture);

        assertDoesNotThrow(() -> publisher.publishQuote(
            "INFY.NS", new Quote("INFY.NS", new BigDecimal("999.98"), new BigDecimal("1000.42"))));
    }

    @Test
    void publishQuoteSwallowsUnexpectedSynchronousFailure() {
        when(kafkaTemplate.send(eq("market-data"), eq("INFY.NS"), anyString()))
            .thenThrow(new RuntimeException("Kafka producer unavailable"));

        assertDoesNotThrow(() -> publisher.publishQuote(
            "INFY.NS", new Quote("INFY.NS", new BigDecimal("999.98"), new BigDecimal("1000.42"))));
    }

    @Test
    void quotePayloadToStringContainsKeyFields() {
        MarketDataPublisher.QuotePayload payload =
            new MarketDataPublisher.QuotePayload("INFY.NS", new BigDecimal("999.98"), new BigDecimal("1000.42"));

        String text = payload.toString();

        assertTrue(text.contains("symbol='INFY.NS'"));
        assertTrue(text.contains("bid=999.98"));
        assertTrue(text.contains("ask=1000.42"));
    }

    private static SendResult<String, String> sendResult() {
        ProducerRecord<String, String> record = new ProducerRecord<>("market-data", "INFY.NS", "payload");
        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition("market-data", 3), 0, 5, 0, 0L, 0, 0);
        return new SendResult<>(record, metadata);
    }
}
