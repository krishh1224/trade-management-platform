package com.team5.portfolio.dto;

import java.math.BigDecimal;

public record SymbolPnlResponse(
    String symbol,
    BigDecimal realisedPnl,
    BigDecimal unrealisedPnl,
    BigDecimal totalPnl
) {
}
