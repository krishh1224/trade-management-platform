package com.team5.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param from      lower bound applied to realised profit and loss, null when unbounded
 * @param bySymbol  present only when the caller asked for the breakdown
 */
public record PnlResponse(
    Long accountId,
    String baseCurrency,
    LocalDate from,
    LocalDate to,
    BigDecimal realisedPnl,
    BigDecimal unrealisedPnl,
    BigDecimal totalPnl,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<SymbolPnlResponse> bySymbol,
    Instant asOf
) {
}
