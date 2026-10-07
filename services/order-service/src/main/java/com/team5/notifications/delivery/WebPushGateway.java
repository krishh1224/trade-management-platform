package com.team5.notifications.delivery;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Real Web Push: encrypts the payload for the browser (RFC 8291, aes128gcm) and signs the request
// with the platform's VAPID key (RFC 8292). Only created when notifications.push.enabled=true.
@Component
@ConditionalOnProperty(prefix = "notifications.push", name = "enabled", havingValue = "true")
class WebPushGateway implements PushGateway {

    // a slow push service must not hold up the dispatcher
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final PushService pushService;
    private final int ttlSeconds;

    WebPushGateway(
        @Value("${notifications.push.vapid-public-key}") String publicKey,
        @Value("${notifications.push.vapid-private-key}") String privateKey,
        @Value("${notifications.push.subject}") String subject,
        @Value("${notifications.push.ttl-seconds:86400}") int ttlSeconds
    ) throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.pushService = new PushService(publicKey, privateKey, subject);
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public int send(String endpoint, String p256dh, String auth, byte[] payload) throws Exception {
        Notification message = new Notification(endpoint, p256dh, auth, payload, ttlSeconds);
        Future<HttpResponse> response = pushService.sendAsync(message, Encoding.AES128GCM);
        try {
            return response.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatusLine().getStatusCode();
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new IllegalStateException("Push service did not answer within " + SEND_TIMEOUT_SECONDS + " s", e);
        }
    }
}
