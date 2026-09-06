package com.team5.domain.enums;

public enum OrderStatus {
    NEW,
    FILLED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}