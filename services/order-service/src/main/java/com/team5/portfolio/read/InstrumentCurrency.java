package com.team5.portfolio.read;

// field order must match the findCurrencies query in PortfolioReadMapper.xml
public record InstrumentCurrency(String symbol, String currency) {
}
