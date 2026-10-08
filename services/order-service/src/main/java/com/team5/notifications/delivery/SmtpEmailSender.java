package com.team5.notifications.delivery;

import com.team5.dto.response.OrderHistoryEntry;
import com.team5.mapper.AccountQueryMapper;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.NotificationType;
import com.team5.notifications.mapper.NotificationMapper;
import com.team5.preferences.AlertChannel;
import jakarta.mail.MessagingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.util.regex.Pattern;
import org.springframework.core.io.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
    private final AccountQueryMapper accountQueryMapper;
    private final String from;
    private final String inboxUrl;

    SmtpEmailSender(
        JavaMailSender mailSender,
        NotificationMapper notificationMapper,
        AccountQueryMapper accountQueryMapper,
        @Value("${notifications.email.from:${spring.mail.username:}}") String from,
        @Value("${notifications.email.inbox-url:http://localhost:4200/notifications}") String inboxUrl
    ) {
        this.mailSender = mailSender;
        this.notificationMapper = notificationMapper;
        this.accountQueryMapper = accountQueryMapper;
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

        try {
            if (notification.getType() == NotificationType.ORDER_HISTORY_EXPORT) {
                sendOrderHistoryExport(to, notification);
            } else {
                sendPlainText(to, notification);
            }
        } catch (MailException e) {
            // becomes the notification's last_error: a short reason, never the credentials
            throw new IllegalStateException("Email provider refused the message: " + shortReason(e), e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the order history PDF attachment", e);
        }
        log.info("Email sent for notification {} ({}) to {}",
            notification.getNotificationId(), notification.getType(), mask(to));
    }

    private void sendPlainText(String to, Notification notification) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from);
        email.setTo(to);
        email.setSubject(subject(notification));
        email.setText(body(notification));
        mailSender.send(email);
    }

    private void sendOrderHistoryExport(String to, Notification notification) throws IOException {
        try {
            var mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject(notification));
            helper.setText(body(notification));
            helper.addAttachment(fileName(notification.getAccountId()),
                new ByteArrayResource(renderOrderHistoryPdf(notification.getAccountId())),
                "application/pdf");
            mailSender.send(mime);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not compose the order history export email", e);
        }
    }

    private String subject(Notification notification) {
        return "Artha: " + notification.getType().title();
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

    private byte[] renderOrderHistoryPdf(long accountId) throws IOException {
        List<OrderHistoryEntry> orders = accountQueryMapper.getOrders(accountId, null, null, null);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            float margin = 40;
            float lineHeight = 13;

            int index = 0;
            boolean includeTitle = true;
            while (includeTitle || index < orders.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                float y = page.getMediaBox().getHeight() - margin;

                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    if (includeTitle) {
                        y = writeLine(stream, PDType1Font.HELVETICA_BOLD, 14, margin, y,
                            "Artha Order History Report", lineHeight);
                        y = writeLine(stream, PDType1Font.HELVETICA, 10, margin, y,
                            "Account: " + accountId + "   Generated: "
                                + OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            lineHeight);
                        y -= lineHeight;
                        includeTitle = false;
                    }

                    y = writeLine(stream, PDType1Font.COURIER_BOLD, 9, margin, y,
                        "Created (UTC)         Symbol      Side Qty      Requested         Filled     Status",
                        lineHeight);
                    y = writeLine(stream, PDType1Font.COURIER, 9, margin, y,
                        "------------------------------------------------------------------------------------------",
                        lineHeight);

                    if (orders.isEmpty()) {
                        writeLine(stream, PDType1Font.COURIER, 10, margin, y, "No orders found for this account.", lineHeight);
                        break;
                    }

                    while (index < orders.size() && y > margin + lineHeight) {
                        y = writeLine(stream, PDType1Font.COURIER, 8.5f, margin, y, format(orders.get(index)), lineHeight);
                        index += 1;
                    }
                }
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private static float writeLine(
        PDPageContentStream stream,
        org.apache.pdfbox.pdmodel.font.PDFont font,
        float size,
        float x,
        float y,
        String text,
        float lineHeight
    ) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
        return y - lineHeight;
    }

    private static String format(OrderHistoryEntry order) {
        String created = order.createdOn() == null ? "-" : order.createdOn().withOffsetSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT));
        String symbol = pad(order.symbol(), 10);
        String side = pad(order.side() == null ? "-" : order.side().name(), 4);
        String qty = String.format(Locale.ROOT, "%4d", order.quantity() == null ? 0 : order.quantity());
        String requested = String.format(Locale.ROOT, "%12s", amount(order.price()));
        String filled = String.format(Locale.ROOT, "%12s", amount(order.executedPrice()));
        String status = order.status() == null ? "-" : order.status().name();
        return String.format(Locale.ROOT, "%s  %s  %s %s  %s  %s  %s", created, symbol, side, qty, requested, filled, status);
    }

    private static String amount(java.math.BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String pad(String value, int width) {
        String safe = value == null ? "-" : value;
        return safe.length() >= width ? safe.substring(0, width) : String.format(Locale.ROOT, "%-" + width + "s", safe);
    }

    private static String fileName(long accountId) {
        return "order-history-" + accountId + ".pdf";
    }

    private static String opening(Notification notification) {
        return switch (notification.getType()) {
            case ORDER_FILLED -> "Good news, your order has been executed.";
            case ORDER_REJECTED -> "We were unable to execute your order.";
            case ORDER_CANCELLED -> "Your order has been cancelled as requested.";
            case PRICE_ALERT -> "A price alert on your watchlist has been triggered.";
            case ORDER_HISTORY_EXPORT -> "As requested, your order history PDF report is attached.";
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
