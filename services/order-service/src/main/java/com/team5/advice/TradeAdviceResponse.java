package com.team5.advice;

import java.time.Instant;
import java.util.List;

public record TradeAdviceResponse(
    Long accountId,
    Instant generatedAt,
    List<AdviceSignalResponse> signals,
    List<SkippedSignalResponse> skipped
) {
}