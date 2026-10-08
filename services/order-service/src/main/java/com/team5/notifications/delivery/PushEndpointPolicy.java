package com.team5.notifications.delivery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

// A push subscription's endpoint is a URL handed to us by the customer's browser, and the platform
// then POSTs to it. Accepting any URL would be a server-side request forgery, so only the browser
// vendors' push services are allowed: https, default port, no credentials, known hosts only.
public final class PushEndpointPolicy {

    static final int MAX_ENDPOINT_LENGTH = 1000;

    private static final Set<String> EXACT_HOSTS = Set.of(
        "fcm.googleapis.com",                 // Chrome, Edge (Chromium)
        "updates.push.services.mozilla.com",  // Firefox
        "web.push.apple.com"                  // Safari
    );

    private static final String WINDOWS_PUSH_SUFFIX = ".notify.windows.com"; // Edge on Windows (WNS)

    private PushEndpointPolicy() {
    }

    public static boolean isAllowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return EXACT_HOSTS.contains(host)
            || (host.endsWith(WINDOWS_PUSH_SUFFIX) && host.length() > WINDOWS_PUSH_SUFFIX.length());
    }

    // p256dh: an uncompressed P-256 point (65 bytes, 0x04 prefix); auth: a 16-byte secret
    public static boolean isValidKeys(String p256dh, String auth) {
        byte[] publicKey = decode(p256dh);
        byte[] secret = decode(auth);
        return publicKey != null && publicKey.length == 65 && publicKey[0] == 0x04
            && secret != null && secret.length == 16;
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            return null;
        }
        try {
            return Base64.getUrlDecoder().decode(value.replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
