package com.team5.domain;

import com.team5.domain.enums.Exchange;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateTradableInstrument() {
        Instrument instrument = createInstrument(true);

        assertTrue(instrument.isTradable());
        assertTrue(validator.validate(instrument).isEmpty());
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
        Instrument instrument = new Instrument(
                1L,
                " ",
                "Reliance Industries",
                "EQUITY",
                "INR",
                Exchange.NSE,
                true
        );

        Set<ConstraintViolation<Instrument>> violations =
                validator.validate(instrument);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("ticker"))
        );
    }

    @Test
    void shouldRejectNullExchange() {
        Instrument instrument = new Instrument(
                1L,
                "RELIANCE",
                "Reliance Industries",
                "EQUITY",
                "INR",
                null,
                true
        );

        Set<ConstraintViolation<Instrument>> violations =
                validator.validate(instrument);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("exchange"))
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