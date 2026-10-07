package com.team5.notifications.delivery;

// Sends one encrypted Web Push message to one browser and returns the push service's HTTP status.
// An interface so WebPushSender can be tested without contacting a real push service.
public interface PushGateway {

    int send(String endpoint, String p256dh, String auth, byte[] payload) throws Exception;
}
