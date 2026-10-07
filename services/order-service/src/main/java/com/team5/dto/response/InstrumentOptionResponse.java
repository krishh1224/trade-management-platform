package com.team5.dto.response;

public record InstrumentOptionResponse(
    String symbol,
    String name,
    String currency,
    String assetClass
) {
}
