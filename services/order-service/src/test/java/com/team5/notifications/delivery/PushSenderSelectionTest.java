package com.team5.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.team5.notifications.mapper.PushSubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// notifications.push.enabled decides which PUSH sender exists; never both, never neither
class PushSenderSelectionTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(PushSubscriptionMapper.class, () -> mock(PushSubscriptionMapper.class))
        .withBean(PushGateway.class, () -> mock(PushGateway.class))
        .withUserConfiguration(LoggingPushSender.class, WebPushSender.class);

    @Test
    void byDefault_theLoggingStubIsUsed() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(LoggingPushSender.class);
            assertThat(ctx).doesNotHaveBean(WebPushSender.class);
        });
    }

    @Test
    void whenEnabled_browserPushReplacesTheStub() {
        context.withPropertyValues("notifications.push.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(WebPushSender.class);
            assertThat(ctx).doesNotHaveBean(LoggingPushSender.class);
        });
    }
}
