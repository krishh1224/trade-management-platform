package com.team5.notifications.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PushEndpointPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://fcm.googleapis.com/fcm/send/abc123:APA91b",
        "https://updates.push.services.mozilla.com/wpush/v2/gAAAAA",
        "https://wns2-par02p.notify.windows.com/w/?token=BQYAAA",
        "https://web.push.apple.com/QGuKQ",
        "https://FCM.googleapis.com:443/fcm/send/abc"
    })
    void browserPushServices_areAllowed(String endpoint) {
        assertTrue(PushEndpointPolicy.isAllowedEndpoint(endpoint));
    }

    // every one of these would let a customer make the platform POST somewhere it should not
    @ParameterizedTest
    @ValueSource(strings = {
        "http://fcm.googleapis.com/fcm/send/abc",           // not https
        "https://10.8.72.147:8085/orders",                  // internal broker
        "https://169.254.169.254/latest/meta-data/",        // cloud metadata service
        "https://localhost/api/v1/orders",                  // the platform itself
        "https://fcm.googleapis.com.evil.example/x",         // lookalike suffix
        "https://evil.example/fcm.googleapis.com",          // host in the path
        "https://user:pass@fcm.googleapis.com/fcm/send/x",  // credentials
        "https://fcm.googleapis.com:8443/fcm/send/x",       // non-default port
        "https://notify.windows.com/x",                     // bare WNS apex is not a push endpoint
        "ftp://fcm.googleapis.com/x",
        "not a url",
        ""
    })
    void anythingElse_isRefused(String endpoint) {
        assertFalse(PushEndpointPolicy.isAllowedEndpoint(endpoint));
    }

    @Test
    void overlongEndpoint_isRefused() {
        assertFalse(PushEndpointPolicy.isAllowedEndpoint("https://fcm.googleapis.com/" + "a".repeat(1000)));
    }

    @Test
    void keys_mustHaveTheBrowserShapes() {
        byte[] point = new byte[65];
        point[0] = 4;
        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(point);
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

        assertTrue(PushEndpointPolicy.isValidKeys(p256dh, auth));
        assertFalse(PushEndpointPolicy.isValidKeys(p256dh, "c2hvcnQ"));
        assertFalse(PushEndpointPolicy.isValidKeys(auth, auth));
        assertFalse(PushEndpointPolicy.isValidKeys("%%%", auth));
        assertFalse(PushEndpointPolicy.isValidKeys(null, auth));
    }
}
