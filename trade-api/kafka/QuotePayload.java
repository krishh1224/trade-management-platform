package com.team5.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Payload for QUOTE events on the market-data topic
// Key: symbol | Source: market-poller
// One message per symbol, never one per batch
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotePayload {

    @JsonProperty("symbol")
    private String symbol; // also the kafka message key

    @JsonProperty("price")
    private BigDecimal price; // last observed price, nothing trades here

    @JsonProperty("bid")
    private BigDecimal bid; // sell side, trades settle here for SELLs

    @JsonProperty("ask")
    private BigDecimal ask; // buy side, trades settle here for BUYs

    @JsonProperty("currency")
    private String currency; // ISO 4217, e.g. USD

    @JsonProperty("change")
    private BigDecimal change; // absolute change vs previous close

    @JsonProperty("changePercent")
    private BigDecimal changePercent; // 0.09 means 0.09%

    @JsonProperty("previousClose")
    private BigDecimal previousClose;

    @JsonProperty("marketState")
    private String marketState; // open, closed, pre, post, unknown

    @JsonProperty("stale")
    private boolean stale; // true if quote is past freshness window

    @JsonProperty("quoteAsOf")
    private OffsetDateTime quoteAsOf; // when Fauxnance observed the price, not poll time

    public QuotePayload() {
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getBid() { return bid; }
    public void setBid(BigDecimal bid) { this.bid = bid; }

    public BigDecimal getAsk() { return ask; }
    public void setAsk(BigDecimal ask) { this.ask = ask; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }

    public String getMarketState() { return marketState; }
    public void setMarketState(String marketState) { this.marketState = marketState; }

    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }

    public OffsetDateTime getQuoteAsOf() { return quoteAsOf; }
    public void setQuoteAsOf(OffsetDateTime quoteAsOf) { this.quoteAsOf = quoteAsOf; }
}