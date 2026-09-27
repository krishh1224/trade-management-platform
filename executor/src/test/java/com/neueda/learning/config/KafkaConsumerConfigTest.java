package com.neueda.learning.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.neueda.learning.exception.PermanentFailureException;
import com.neueda.learning.exception.TransientFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    KafkaConsumerConfigTest() {
        // @Value fields are never injected here (no Spring context), so they default to
        // Java's zero values; set them to the same defaults the @Value annotations declare.
        ReflectionTestUtils.setField(config, "retryInitialIntervalMs", 100L);
        ReflectionTestUtils.setField(config, "retryMaxIntervalMs", 5000L);
        ReflectionTestUtils.setField(config, "retryMultiplier", 2.0);
        ReflectionTestUtils.setField(config, "retryMaxAttempts", 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandlerBeanBuildsSuccessfully() {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = config.kafkaErrorHandler(kafkaTemplate);

        assertNotNull(handler);
    }

    @Test
    void resolveFailureReasonReturnsUnknownForNullException() {
        String reason = ReflectionTestUtils.invokeMethod(config, "resolveFailureReason", (Throwable) null);

        assertEquals("UNKNOWN_FAILURE", reason);
    }

    @Test
    void resolveFailureReasonReadsPermanentFailureReason() {
        PermanentFailureException exception = new PermanentFailureException("MALFORMED_JSON", "bad json");

        String reason = ReflectionTestUtils.invokeMethod(config, "resolveFailureReason", exception);

        assertEquals("MALFORMED_JSON", reason);
    }

    @Test
    void resolveFailureReasonReadsTransientFailureReason() {
        TransientFailureException exception = new TransientFailureException("DB_CONNECTION_LOST", "db down");

        String reason = ReflectionTestUtils.invokeMethod(config, "resolveFailureReason", exception);

        assertEquals("DB_CONNECTION_LOST", reason);
    }

    @Test
    void resolveFailureReasonMapsDeserializationExceptionToMalformedJson() {
        // No cause on purpose: unwrap() follows the cause chain to its root before the
        // instanceof checks run, so a DeserializationException wrapping a cause would never
        // itself be classified here — only a cause-less one survives unwrap() unchanged.
        DeserializationException exception =
            new DeserializationException("bad payload", new byte[0], false, null);

        String reason = ReflectionTestUtils.invokeMethod(config, "resolveFailureReason", exception);

        assertEquals("MALFORMED_JSON", reason);
    }

    @Test
    void resolveFailureReasonFallsBackToSimpleClassNameForUnknownException() {
        IllegalStateException exception = new IllegalStateException("something else broke");

        String reason = ReflectionTestUtils.invokeMethod(config, "resolveFailureReason", exception);

        assertEquals("IllegalStateException", reason);
    }

    @Test
    void resolveFailureReasonUnwrapsListenerExecutionFailedExceptionFirst() {
        PermanentFailureException root = new PermanentFailureException("ORDER_NOT_FOUND", "order missing");
        ListenerExecutionFailedException wrapper =
            new ListenerExecutionFailedException("listener failed", root);

        String reason = ReflectionTestUtils.invokeMethod(config, "resolveFailureReason", wrapper);

        assertEquals("ORDER_NOT_FOUND", reason);
    }

    @Test
    void unwrapFollowsCauseChainToRootCause() {
        RuntimeException root = new RuntimeException("root");
        RuntimeException middle = new RuntimeException("middle", root);
        RuntimeException top = new RuntimeException("top", middle);

        Throwable unwrapped = ReflectionTestUtils.invokeMethod(config, "unwrap", top);

        assertEquals(root, unwrapped);
    }

    @Test
    void unwrapReturnsSameExceptionWhenThereIsNoCause() {
        RuntimeException noCause = new RuntimeException("standalone");

        Throwable unwrapped = ReflectionTestUtils.invokeMethod(config, "unwrap", noCause);

        assertEquals(noCause, unwrapped);
    }
}
