package com.team5.watchlists.domain;

public enum PriceAlertStatus {
    // evaluated against every market-data quote for its symbol
    ACTIVE,
    // crossed once and handed to notifications; never evaluated again (one-shot)
    TRIGGERED
}
