package com.team5.notifications.web;

// What the browser needs to subscribe. publicKey is the VAPID public key (not a secret);
// null when push is switched off on this server.
public record PushConfigResponse(boolean enabled, String publicKey) {
}
