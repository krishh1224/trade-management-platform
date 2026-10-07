package com.team5.notifications.exception;

import com.team5.domain.exception.DomainException;

// Also used when the notification exists but belongs to another account, so a 404 never
// confirms that someone else's notification exists
public class NotificationNotFoundException extends DomainException {

    public NotificationNotFoundException() {
        super("NTF-404", "Notification not found");
    }
}
