package com.team5.domain.exception;

public class InstrumentNotFoundException extends DomainException {

    public InstrumentNotFoundException() {
        super("INS-404", "Instrument not found");
    }
}