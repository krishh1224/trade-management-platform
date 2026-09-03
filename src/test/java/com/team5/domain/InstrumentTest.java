package com.team5.domain;

import com.team5.domain.enums.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {

    @Test
    void shouldCreateTradableInstrument() {
        Instrument instrument = createInstrument(true);

        assertTrue(instrument.isTradable());
    }

    @Test
    void shouldCreateNonTradableInstrument() {
        Instrument instrument = createInstrument(false);

        assertFalse(instrument.isTradable());
    }

    @Test
    void shouldMakeInstrumentNonTradableAfterDelisting() {
        Instrument instrument = createInstrument(true);

        instrument.delist();

        assertFalse(instrument.isTradable());
    }

    @Test
    void shouldRejectBlankTicker() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        1L,
                        " ",
                        "Reliance Industries",
                        "EQUITY",
                        "INR",
                        Exchange.NSE,
                        true
                )
        );
    }

    @Test
    void shouldRejectNullExchange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        1L,
                        "RELIANCE",
                        "Reliance Industries",
                        "EQUITY",
                        "INR",
                        null,
                        true
                )
        );
    }

    private Instrument createInstrument(boolean tradable) {
        return new Instrument(
                1L,
                "RELIANCE",
                "Reliance Industries",
                "EQUITY",
                "INR",
                Exchange.NSE,
                tradable
        );
    }
}