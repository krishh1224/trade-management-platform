package com.neueda.learning.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstrumentRecordTest {

    @Test
    void allArgsConstructorPopulatesAllGetters() {
        InstrumentRecord record = new InstrumentRecord(
            301L, "INFY.NS", "Infosys Limited", "EQUITY", "INR", "NSE", true);

        assertEquals(301L, record.getInstrumentId());
        assertEquals("INFY.NS", record.getTicker());
        assertEquals("Infosys Limited", record.getName());
        assertEquals("EQUITY", record.getAssetClass());
        assertEquals("INR", record.getCurrency());
        assertEquals("NSE", record.getExchange());
        assertEquals(true, record.getTradable());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        InstrumentRecord record = new InstrumentRecord();

        record.setInstrumentId(303L);
        record.setTicker("TATASTEEL.BO");
        record.setName("Tata Steel Limited");
        record.setAssetClass("EQUITY");
        record.setCurrency("INR");
        record.setExchange("BSE");
        record.setTradable(false);

        assertEquals(303L, record.getInstrumentId());
        assertEquals("TATASTEEL.BO", record.getTicker());
        assertEquals("Tata Steel Limited", record.getName());
        assertEquals("EQUITY", record.getAssetClass());
        assertEquals("INR", record.getCurrency());
        assertEquals("BSE", record.getExchange());
        assertEquals(false, record.getTradable());
    }

    @Test
    void toStringContainsKeyFields() {
        InstrumentRecord record = new InstrumentRecord(
            301L, "INFY.NS", "Infosys Limited", "EQUITY", "INR", "NSE", true);

        String text = record.toString();

        assertTrue(text.contains("ticker='INFY.NS'"));
        assertTrue(text.contains("exchange='NSE'"));
        assertTrue(text.contains("tradable=true"));
    }
}
