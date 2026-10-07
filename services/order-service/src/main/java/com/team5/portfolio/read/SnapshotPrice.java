package com.team5.portfolio.read;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// field order must match the findSnapshots query in PortfolioReadMapper.xml
public record SnapshotPrice(String symbol, BigDecimal lastPrice, String currency, LocalDateTime updatedAt) {
}
