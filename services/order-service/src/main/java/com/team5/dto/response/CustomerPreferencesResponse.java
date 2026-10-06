package com.team5.dto.response;

public record CustomerPreferencesResponse(
    String currency,
    String theme,
    String alertChannel
) {
}