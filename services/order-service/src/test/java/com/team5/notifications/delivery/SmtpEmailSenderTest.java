package com.team5.notifications.delivery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.mapper.AccountQueryMapper;
import com.team5.notifications.InMemoryNotificationMapper;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationStatus;
import com.team5.notifications.domain.NotificationType;
import com.team5.preferences.AlertChannel;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    private static final long ACCOUNT = 101L;
    private static final String ADDRESS = "jane.doe@gmail.com";

    private InMemoryNotificationMapper mapper;
    private JavaMailSender mailSender;
    private AccountQueryMapper accountQueryMapper;
    private SmtpEmailSender sender;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryNotificationMapper();
        mailSender = mock(JavaMailSender.class);
        accountQueryMapper = mock(AccountQueryMapper.class);
        sender = new SmtpEmailSender(
            mailSender,
            mapper,
            accountQueryMapper,
            "alerts@artha.test",
            "http://localhost:4200/notifications"
        );

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(SmtpEmailSender.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SmtpEmailSender.class)).detachAppender(logs);
    }

    @Test
    void sendsToTheAddressOnFileForTheNotificationsAccount() {
        mapper.storeEmail(ACCOUNT, ADDRESS);

        sender.send(notification(NotificationType.ORDER_REJECTED, "Your BUY order for 1 TSLA was rejected (insufficient funds)."));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage email = sent.getValue();
        assertArrayEquals(new String[] {ADDRESS}, email.getTo());
        assertEquals("alerts@artha.test", email.getFrom());
        assertEquals("Artha: Order rejected", email.getSubject());
        assertEquals("""
            Dear Customer,

            We were unable to execute your order.

            Your BUY order for 1 TSLA was rejected (insufficient funds).

            You can see all your order updates in Artha:
            http://localhost:4200/notifications

            Thank you for trading with Artha.

            Regards,
            Team Artha

            --
            You are receiving this email because your alert channel is set to Email. You can change this on your Profile page in Artha.
            This is an automated message, please do not reply.""", email.getText());
    }

    @Test
    void openingLineMatchesTheOutcome() {
        mapper.storeEmail(ACCOUNT, ADDRESS);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);

        sender.send(notification(NotificationType.ORDER_FILLED, "Your BUY order for 1 INFY.NS was filled at 1004.57."));
        sender.send(notification(NotificationType.ORDER_CANCELLED, "Your BUY order for 1 INFY.NS was cancelled."));

        verify(mailSender, times(2)).send(sent.capture());
        assertTrue(sent.getAllValues().get(0).getText().contains("Good news, your order has been executed."));
        assertEquals("Artha: Order filled", sent.getAllValues().get(0).getSubject());
        assertTrue(sent.getAllValues().get(1).getText().contains("Your order has been cancelled as requested."));
        assertEquals("Artha: Order cancelled", sent.getAllValues().get(1).getSubject());
    }

    @Test
    void anotherAccountsAddressIsNeverUsed() {
        mapper.storeEmail(202L, "someone.else@gmail.com");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        assertEquals("No valid email address on file", error.getMessage());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void invalidAddressOnFile_isRefusedWithoutContactingTheProvider() {
        mapper.storeEmail(ACCOUNT, "not an address");

        assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void providerFailure_becomesAShortReasonForTheNotification() {
        mapper.storeEmail(ACCOUNT, ADDRESS);
        doThrow(new MailAuthenticationException("535-5.7.8 Username and Password not accepted"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled.")));

        assertTrue(error.getMessage().startsWith("Email provider refused the message: "));
        assertTrue(error.getMessage().contains("Username and Password not accepted"));
    }

    @Test
    void failureIsRecordedOnTheNotificationByTheDispatcher() {
        mapper.storeEmail(ACCOUNT, ADDRESS);
        doThrow(new MailAuthenticationException("535 bad credentials")).when(mailSender).send(any(SimpleMailMessage.class));
        Notification queued = notification(NotificationType.ORDER_FILLED, "Your order was filled.");
        mapper.insertIfAbsent(queued);
        NotificationDispatcher dispatcher = new NotificationDispatcher(mapper, accountId -> Optional.of(AlertChannel.EMAIL),
            new ChannelSenderRegistry(List.of(sender)), new DeliveryLedger(mapper), 50);

        dispatcher.dispatchPending();

        Notification failed = mapper.rows().get(0);
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        assertEquals(AlertChannel.EMAIL, failed.getChannel());
        assertTrue(failed.getLastError().contains("bad credentials"));
    }

    @Test
    void theAddressIsMaskedInTheLog() {
        mapper.storeEmail(ACCOUNT, ADDRESS);

        sender.send(notification(NotificationType.ORDER_FILLED, "Your order was filled."));

        String logged = logs.list.get(0).getFormattedMessage();
        assertTrue(logged.contains("j***@gmail.com"));
        assertFalse(logged.contains(ADDRESS));
    }

    @Test
    void mask_keepsOnlyTheFirstCharacterAndTheDomain() {
        assertEquals("j***@gmail.com", SmtpEmailSender.mask("jane.doe@gmail.com"));
        assertEquals("***", SmtpEmailSender.mask("@gmail.com"));
    }

    @Test
    void orderHistoryExport_sendsAPdfAttachment() throws Exception {
        mapper.storeEmail(ACCOUNT, ADDRESS);
        when(accountQueryMapper.getOrders(ACCOUNT, null, null, null)).thenReturn(List.of(
            new OrderHistoryEntry(
                "ORD-42",
                ACCOUNT,
                "INFY.NS",
                OrderSide.BUY,
                5,
                new BigDecimal("1500.00"),
                new BigDecimal("1498.75"),
                OrderStatus.FILLED,
                "idem-42",
                OffsetDateTime.parse("2026-10-07T09:00:00Z"),
                new BigDecimal("7493.75")
            )
        ));

        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        sender.send(notification(NotificationType.ORDER_HISTORY_EXPORT,
            "Your order history PDF report is ready and attached to this email."));

        verify(mailSender).send(mime);
        assertEquals("Artha: Order history report", mime.getSubject());
        Object content = mime.getContent();
        assertTrue(content instanceof Multipart);
        Multipart multipart = (Multipart) content;

        boolean hasPdf = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String filename = part.getFileName();
            String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase();
            if ((filename != null && filename.toLowerCase().contains(".pdf"))
                || contentType.contains("application/pdf")) {
                hasPdf = true;
                break;
            }
        }
        assertTrue(hasPdf);
    }

    private static Notification notification(NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setNotificationId(7L);
        notification.setEventId(UUID.randomUUID());
        notification.setAccountId(ACCOUNT);
        notification.setType(type);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setCreatedAt(OffsetDateTime.now());
        return notification;
    }
}
