package com.team5.notifications.api;

// The entry point watchlists calls to have a triggered price alert delivered.
// Deliberately a Java interface and not an HTTP route: a route would let a customer send
// themselves, or anyone, arbitrary content.
public interface AlertDelivery {

    // Records the alert for delivery on the account's preferred channel and returns once it is
    // durable. Returns false when this alertId was already recorded (nothing new is sent).
    boolean deliver(PriceAlert alert);
}
