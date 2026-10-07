package com.team5.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.team5.notifications.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

// notifications.email.enabled decides which EMAIL sender exists; never both, never neither
class EmailSenderSelectionTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
        .withBean(NotificationMapper.class, () -> mock(NotificationMapper.class))
        .withUserConfiguration(LoggingEmailSender.class, SmtpEmailSender.class);

    @Test
    void byDefault_theLoggingStubIsUsed() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(LoggingEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
        });
    }

    @Test
    void whenEnabled_realEmailReplacesTheStub() {
        context.withPropertyValues("notifications.email.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(SmtpEmailSender.class);
            assertThat(ctx).doesNotHaveBean(LoggingEmailSender.class);
        });
    }

    @Test
    void whenDisabledExplicitly_theLoggingStubIsUsed() {
        context.withPropertyValues("notifications.email.enabled=false").run(ctx -> {
            assertThat(ctx).hasSingleBean(LoggingEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
        });
    }
}
