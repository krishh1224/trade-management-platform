package com.team5.notifications.exception;

import com.team5.domain.exception.DomainException;

// The browser's subscription is not one we will ever POST to: unknown push host, plain http,
// a port or credentials in the URL, or keys of the wrong shape
public class InvalidPushSubscriptionException extends DomainException {

    public InvalidPushSubscriptionException() {
        super("VAL-422", "Invalid input");
    }
}
