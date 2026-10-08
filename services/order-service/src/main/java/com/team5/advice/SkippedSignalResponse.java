package com.team5.advice;

public record SkippedSignalResponse(
    String symbol,
    String instrumentName,
    String origin,
    String reason
) {
}