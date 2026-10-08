package com.team5.dto.response;

public record ErrorResponse(
    String errorCode,
    String message
) {
}
