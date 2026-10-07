package com.team5.notifications.delivery;

import com.team5.notifications.domain.Notification;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.preferences.AlertChannel;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Real EMAIL channel, active when notifications.email.enabled=true (otherwise LoggingEmailSender).
// The recipient is the address on file for the notification's account, looked up here, so no
// caller can point an email somewhere else. The body is the notification's message, which
// MessageFormatter already builds from structured fields only.
@Component
@ConditionalOnProperty(prefix = "notifications.email", name = "enabled", havingValue = "true")
class SmtpEmailSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    // deliberately simple: rejects blanks, spaces and anything without one @ and a dotted domain
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_ERROR_LENGTH = 300;

    private final JavaMailSender mailSender;
    private final NotificationMapper notificationMapper;
    private final String from;
    private final String inboxUrl;

    SmtpEmailSender(
        JavaMailSender mailSender,
        NotificationMapper notificationMapper,
        @Value("${notifications.email.from:${spring.mail.username:}}") String from,
        @Value("${notifications.email.inbox-url:http://localhost:4200/notifications}") String inboxUrl
    ) {
        this.mailSender = mailSender;
        this.notificationMapper = notificationMapper;
        this.from = from;
        this.inboxUrl = inboxUrl;
    }

    @Override
    public AlertChannel channel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        String to = notificationMapper.findEmailForAccount(notification.getAccountId());
        if (to == null || !EMAIL.matcher(to.trim()).matches()) {
            throw new IllegalStateException("No valid email address on file");
        }
        to = to.trim();

        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from);
        email.setTo(to);
        email.setSubject("Artha: " + notification.getType().title());
        email.setText(body(notification));

        try {
            mailSender.send(email);
        } catch (MailException e) {
            // becomes the notification's last_error: a short reason, never the credentials
            throw new IllegalStateException("Email provider refused the message: " + shortReason(e), e);
        }
        log.info("Email sent for notification {} ({}) to {}",
            notification.getNotificationId(), notification.getType(), mask(to));
    }

    private String body(Notification notification) {
        return "Dear Customer,\n\n"
            + opening(notification) + "\n\n"
            + notification.getMessage() + "\n\n"
            + "You can see all your order updates in Artha:\n"
            + inboxUrl + "\n\n"
            + "Thank you for trading with Artha.\n\n"
            + "Regards,\n"
            + "Team Artha\n\n"
            + "--\n"
            + "You are receiving this email because your alert channel is set to Email. "
            + "You can change this on your Profile page in Artha.\n"
            + "This is an automated message, please do not reply.";
    }

    private static String opening(Notification notification) {
        return switch (notification.getType()) {
            case ORDER_FILLED -> "Good news, your order has been executed.";
            case ORDER_REJECTED -> "We were unable to execute your order.";
            case ORDER_CANCELLED -> "Your order has been cancelled as requested.";
            case PRICE_ALERT -> "A price alert on your watchlist has been triggered.";
        };
    }

    private static String shortReason(MailException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String reason = root.getClass().getSimpleName()
            + (root.getMessage() == null ? "" : ": " + root.getMessage().replaceAll("\\s+", " ").trim());
        return reason.length() <= MAX_ERROR_LENGTH ? reason : reason.substring(0, MAX_ERROR_LENGTH);
    }

    // jane.doe@gmail.com -> j***@gmail.com
    static String mask(String address) {
        int at = address.indexOf('@');
        return at <= 0 ? "***" : address.charAt(0) + "***" + address.substring(at);
    }
}
