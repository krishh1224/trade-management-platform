package com.team5.domain;

import com.team5.domain.enums.Exchange;

public class Instrument {

    private final long instrumentId;

    private final String ticker;

    private final String name;

    private final String assetClass;

    private final String currency;

    private final Exchange exchange;

    private boolean tradable;

    public Instrument(long instrumentId, String ticker, String name,
                      String assetClass, String currency, Exchange exchange,
                      boolean tradable) {
        if (ticker == null || ticker.isBlank())     throw new IllegalArgumentException("ticker must not be blank.");
        if (name == null || name.isBlank())         throw new IllegalArgumentException("Instrument name must not be blank.");
        if (assetClass == null || assetClass.isBlank()) throw new IllegalArgumentException("assetClass must not be blank.");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank.");
        if (exchange == null)                       throw new IllegalArgumentException("exchange must not be null.");

        this.instrumentId = instrumentId;
        this.ticker       = ticker;
        this.name         = name;
        this.assetClass   = assetClass;
        this.currency     = currency;
        this.exchange     = exchange;
        this.tradable     = tradable;
    }

    public boolean isTradable() { return tradable; }

    public void delist() { this.tradable = false; }

    public long getInstrumentId() { return instrumentId; }
    public String getTicker()     { return ticker; }
    public String getName()       { return name; }
    public String getAssetClass() { return assetClass; }
    public String getCurrency()   { return currency; }
    public Exchange getExchange() { return exchange; }

    @Override
    public String toString() {
        return "Instrument{instrumentId=" + instrumentId + ", ticker='" + ticker
            + "', exchange=" + exchange + ", tradable=" + tradable + "}";
    }
}