package com.team5.portfolio.pnl;

import java.math.BigDecimal;

// field order must match the sumRealisedBySymbol query in PnlLedgerMapper.xml
public record SymbolRealised(String symbol, BigDecimal realisedPnl) {
}
