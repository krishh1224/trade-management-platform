package com.team5.notifications.domain;

public enum NotificationType {
    ORDER_FILLED("Order filled"),
    ORDER_REJECTED("Order rejected"),
    ORDER_CANCELLED("Order cancelled"),
    PRICE_ALERT("Price alert");

    // the heading shown to the customer; derived here rather than stored on every row
    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
