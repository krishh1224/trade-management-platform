package com.team5.domain;

import com.team5.domain.enums.Exchange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class Instrument {

    private final long instrumentId;

    @NotBlank
    private final String ticker;

    @NotBlank
    private final String name;

    @NotBlank
    private final String assetClass;

    @NotBlank
    @Size(min=3,max=3)
    private final String currency;

    @NotNull
    private final Exchange exchange;

    private boolean tradable;

    public Instrument(long instrumentId, String ticker, String name,
                      String assetClass, String currency, Exchange exchange,
                      boolean tradable) {
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