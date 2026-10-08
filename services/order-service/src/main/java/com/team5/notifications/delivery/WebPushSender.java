package com.team5.notifications.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team5.notifications.domain.Notification;
import com.team5.notifications.domain.PushSubscription;
import com.team5.notifications.mapper.PushSubscriptionMapper;
import com.team5.preferences.AlertChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Real PUSH channel, active when notifications.push.enabled=true (otherwise LoggingPushSender).
// Sends a browser pop-up to every browser the notification's account enabled push on. The browsers
// come from push_subscriptions for that account only, and every endpoint is checked against the
// push-service allowlist again before anything is sent.
@Component
@ConditionalOnProperty(prefix = "notifications.push", name = "enabled", havingValue = "true")
class WebPushSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final PushSubscriptionMapper subscriptionMapper;
    private final PushGateway pushGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    WebPushSender(PushSubscriptionMapper subscriptionMapper, PushGateway pushGateway) {
        this.subscriptionMapper = subscriptionMapper;
        this.pushGateway = pushGateway;
    }

    @Override
    public AlertChannel channel() {
        return AlertChannel.PUSH;
    }

    @Override
    public void send(Notification notification) {
        List<PushSubscription> browsers = subscriptionMapper.findByAccount(notification.getAccountId());
        if (browsers.isEmpty()) {
            throw new IllegalStateException("Push is not enabled on any browser for this account");
        }

        byte[] payload = payload(notification);
        int delivered = 0;
        List<String> failures = new ArrayList<>();

        for (PushSubscription browser : browsers) {
            if (!PushEndpointPolicy.isAllowedEndpoint(browser.endpoint())) {
                subscriptionMapper.deleteById(browser.subscriptionId());
                failures.add("endpoint not allowed");
                continue;
            }
            try {
                int status = pushGateway.send(browser.endpoint(), browser.p256dh(), browser.auth(), payload);
                if (status >= 200 && status < 300) {
                    delivered++;
                } else if (status == 404 || status == 410) {
                    // the browser unsubscribed or the subscription expired: forget it
                    subscriptionMapper.deleteById(browser.subscriptionId());
                    failures.add("subscription expired (HTTP " + status + ")");
                } else {
                    failures.add("HTTP " + status);
                }
            } catch (Exception e) {
                failures.add(e.getClass().getSimpleName());
            }
        }

        if (delivered == 0) {
            throw new IllegalStateException("Push not delivered: " + String.join(", ", failures));
        }
        log.info("Push sent for notification {} ({}) to {} browser(s) of account {}",
            notification.getNotificationId(), notification.getType(), delivered, notification.getAccountId());
    }

    // what the service worker shows; built only from the already-sanitised title and message
    private byte[] payload(Notification notification) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", "Artha: " + notification.getType().title());
        body.put("body", notification.getMessage());
        body.put("url", "/notifications");
        body.put("tag", "artha-notification-" + notification.getNotificationId());
        try {
            return objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not build the push payload", e);
        }
    }
}
