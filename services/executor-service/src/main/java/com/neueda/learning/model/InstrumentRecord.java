package com.neueda.learning.model;

/**
 * Database record for Instrument entity.
 */
public class InstrumentRecord {
    private Long instrumentId;
    private String ticker;
    private String name;
    private String assetClass;
    private String currency;
    private Boolean tradable;

    public InstrumentRecord() {
    }

    public InstrumentRecord(Long instrumentId, String ticker, String name, String assetClass,
                            String currency, Boolean tradable) {
        this.instrumentId = instrumentId;
        this.ticker = ticker;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.tradable = tradable;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Boolean getTradable() {
        return tradable;
    }

    public void setTradable(Boolean tradable) {
        this.tradable = tradable;
    }

    @Override
    public String toString() {
        return "InstrumentRecord{" +
                "instrumentId=" + instrumentId +
                ", ticker='" + ticker + '\'' +
                ", name='" + name + '\'' +
                ", assetClass='" + assetClass + '\'' +
                ", currency='" + currency + '\'' +
                ", tradable=" + tradable +
                '}';
    }
}

