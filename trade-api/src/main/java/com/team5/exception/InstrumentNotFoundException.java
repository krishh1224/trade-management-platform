package com.team5.exception;

public class InstrumentNotFoundException extends DomainException {

    public InstrumentNotFoundException() {
        super("INS-404", "Instrument not found");
    }
}